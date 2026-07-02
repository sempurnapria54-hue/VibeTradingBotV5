package com.example.tradingbot.domain.command.action;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.config.KillSwitchProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.deal.DealContextService;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Аварийный executor kill-switch — снятие риска по инструменту сделки с
 * подтверждением сверкой реального состояния биржи. <b>Не действие стратегии и
 * не команда:</b> зовётся программно safety-flow ({@code SafetyHoldCoordinator})
 * и AnomalyJob, в стратегии не объявляется. teardown идёт прямыми best-effort
 * вызовами {@code IntegrationService}, подтверждение — дёрганьем REFRESH-команд
 * через диспетчер (резолвер статуса живёт в REFRESH-executor'ах, здесь не
 * дублируется).
 *
 * <p><b>Порядок teardown фиксирован инвариантом</b> «защита живёт при открытой
 * позиции»: close позиции первым → отмена ordinary orders → отмена algo-защит →
 * безусловный страховочный close (доисполнение entry-ордера во время отмен).
 *
 * <p><b>Подтверждение — рефрешем.</b> После teardown обновляем FSM-известные
 * сущности сделки поимённо (REFRESH_POSITION/ORDER/ALGO_ORDER), перечитываем
 * граф из БД и проверяем flat по доменным моделям ({@code hasLiveRisk}/
 * {@code isLive}). Не flat — повторяем, bounded лимитом попыток из
 * {@link KillSwitchProperties}. Лимит исчерпан → {@code failure} (эскалацию
 * L3→биржа держит {@code SafetyHoldCoordinator}, HOLD-Q1). Orphan-сущности вне
 * модели сделки — зона AnomalyJob (ANOM-Q2, шаг 8). См.
 * docs/components/KillSwitchExecutor.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KillSwitchExecutor {

    /** Фолбэк-лимит попыток teardown, если не задан в конфиге. */
    private static final int DEFAULT_TEARDOWN_ATTEMPTS = 3;

    private final IntegrationService integrationService;
    private final ServiceCommandExecutor serviceCommandExecutor;
    private final DealContextService dealContextService;
    private final KillSwitchProperties properties;

    public ServiceCommandExecutionResult execute(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        String instId = dealContext.getInstrument().getExternalId();
        int maxAttempts = teardownAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            teardown(deal, instId);
            if (confirmedFlatSafely(deal, dealContext)) {
                return ServiceCommandExecutionResult.ok();
            }
            log.warn("Kill-switch teardown not confirmed flat instId={} attempt={}/{}", instId, attempt, maxAttempts);
        }
        return ServiceCommandExecutionResult.failure(RuntimeErrorCode.EXCHANGE_ERROR,
                "Kill-switch could not confirm flat instId=" + instId + " after " + maxAttempts + " attempts");
    }

    /**
     * Один проход teardown прямыми best-effort вызовами. Порядок фиксирован
     * инвариантом «защита живёт при открытой позиции»: close позиции (её защита
     * ещё стоит) → отмена ordinary orders → отмена algo-защит → безусловный
     * страховочный close.
     */
    private void teardown(Deal deal, String instId) {
        closePositionIfLive(deal, instId);
        cancelLiveOrders(deal, instId);
        cancelLiveAlgoOrders(deal, instId);
        closeBestEffort(instId);
    }

    private void closePositionIfLive(Deal deal, String instId) {
        if (isTrue(deal.hasLivePositionRisk())) {
            closeBestEffort(instId);
        }
    }

    /** Безусловный best-effort close: на flat-позиции биржа вернёт «нет позиции» — гасим. */
    private void closeBestEffort(String instId) {
        try {
            integrationService.closePosition(instId, null);
        } catch (RuntimeException e) {
            log.warn("Kill-switch close-position best-effort failure instId={}: {}", instId, e.getMessage());
        }
    }

    private void cancelLiveOrders(Deal deal, String instId) {
        deal.liveOrders().forEach(order -> cancelOrderBestEffort(order, instId));
    }

    private void cancelOrderBestEffort(Order order, String instId) {
        try {
            integrationService.cancelOrder(order, instId);
        } catch (RuntimeException e) {
            log.warn("Kill-switch cancel-order best-effort failure orderId={}: {}", order.getId(), e.getMessage());
        }
    }

    private void cancelLiveAlgoOrders(Deal deal, String instId) {
        deal.liveAlgoOrders().forEach(algoOrder -> cancelAlgoBestEffort(algoOrder, instId));
    }

    private void cancelAlgoBestEffort(AlgoOrder algoOrder, String instId) {
        try {
            integrationService.cancelAlgoOrder(algoOrder, instId);
        } catch (RuntimeException e) {
            log.warn("Kill-switch cancel-algo best-effort failure algoOrderId={}: {}", algoOrder.getId(), e.getMessage());
        }
    }

    /**
     * Сверка: обновляем FSM-известные сущности сделки REFRESH-командами
     * (evidence-cycle + резолвер статуса внутри executor'ов), перечитываем граф
     * из БД, проверяем flat по доменным моделям. Транзиентная ошибка чтения — не
     * flat в этой попытке (не срываем bounded-цикл).
     */
    private boolean confirmedFlatSafely(Deal deal, DealContext dealContext) {
        try {
            refreshEntities(deal, dealContext);
            dealContextService.reloadRuntimeGraph(deal);
            return isFlat(deal);
        } catch (RuntimeException e) {
            log.warn("Kill-switch flat-confirm failed dealId={}: {}", deal.getId(), e.getMessage());
            return false;
        }
    }

    private void refreshEntities(Deal deal, DealContext dealContext) {
        serviceCommandExecutor.execute(refreshPositionCommand(deal), dealContext);
        deal.liveOrders().forEach(order ->
                serviceCommandExecutor.execute(refreshOrderCommand(deal, order.getId()), dealContext));
        deal.liveAlgoOrders().forEach(algoOrder ->
                serviceCommandExecutor.execute(refreshAlgoOrderCommand(deal, algoOrder.getId()), dealContext));
    }

    private boolean isFlat(Deal deal) {
        return isFalse(deal.hasLivePositionRisk())
                && isEmpty(deal.liveOrders())
                && isEmpty(deal.liveAlgoOrders());
    }

    private ServiceCommand refreshPositionCommand(Deal deal) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_POSITION)
                .dealId(deal.getId())
                .build();
    }

    private ServiceCommand refreshOrderCommand(Deal deal, Long orderId) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ORDER)
                .dealId(deal.getId())
                .payload(new RefreshOrderCommandPayload(orderId))
                .build();
    }

    private ServiceCommand refreshAlgoOrderCommand(Deal deal, Long algoOrderId) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ALGO_ORDER)
                .dealId(deal.getId())
                .payload(new RefreshAlgoOrderCommandPayload(algoOrderId))
                .build();
    }

    private int teardownAttempts() {
        Integer maxAttempts = properties.getMaxTeardownAttempts();
        return nonNull(maxAttempts) && maxAttempts > 0 ? maxAttempts : DEFAULT_TEARDOWN_ATTEMPTS;
    }
}
