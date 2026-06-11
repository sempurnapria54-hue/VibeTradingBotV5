package com.example.tradingbot.domain.command.executor;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.integration.service.IntegrationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Исполняет EXECUTE_KILL_SWITCH — аварийное снятие риска по runtime-графу
 * DealContext.deal (только live-сущности); RiskValidator не вызывается.
 *
 * <p><b>Порядок — риск-минимизирующий, снижаем риск максимально быстро:</b>
 * <ol>
 *   <li>закрытие позиции <b>первым</b> — это доминирующий live market
 *       risk; close (market reduce-only, autoCxl снимает resting-ордера
 *       на бирже) флэтит экспозицию за один вызов;</li>
 *   <li>отмена оставшихся ordinary orders — предотвратить re-entry
 *       (entry-ордер мог не попасть под autoCxl);</li>
 *   <li>отмена algo-защит <b>последними</b> — они reduce-only: пока
 *       позиция жива, это SL/TP-защита (снимать её до close — окно без
 *       защиты), после flat — безвредный cleanup;</li>
 *   <li>повторный close <b>в конце — безусловный</b> (closeBestEffort,
 *       не по графу): entry-ордер мог исполниться во время отмен и
 *       открыть позицию с нуля, а runtime-граф снят на старте команды и
 *       этого не видит. На уже flat-позиции биржа вернёт «нет позиции» —
 *       ошибку гасим, чтобы не валить kill-switch.</li>
 * </ol>
 *
 * <p>Подтверждение факта — отдельными REFRESH_*. (Полный kill-switch flow
 * — report/after-snapshot — backlog п.7.) См.
 * docs/components/KillSwitchExecutor.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KillSwitchExecutor implements CommandExecutor {

    private final IntegrationService integrationService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.EXECUTE_KILL_SWITCH;
    }

    @Override
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        String instId = dealContext.getInstrument().getExternalId();
        closeLivePosition(deal, instId);
        cancelLiveOrders(deal, instId);
        cancelLiveAlgoOrders(deal, instId);
        closeBestEffort(instId);
        return ServiceCommandExecutionResult.ok();
    }

    private void closeLivePosition(Deal deal, String instId) {
        Position position = deal.getPosition();
        if (nonNull(position) && isTrue(position.hasLiveRisk())) {
            closeBestEffort(instId);
        }
    }

    private void closeBestEffort(String instId) {
        try {
            integrationService.closePosition(instId, null);
        } catch (RuntimeException e) {
            log.warn("Kill-switch close-position best-effort failure instId={}: {}", instId, e.getMessage());
        }
    }

    private void cancelLiveOrders(Deal deal, String instId) {
        safe(deal.getOrders()).stream()
                .filter(order -> isTrue(order.isLive()))
                .forEach(order -> cancelOrderBestEffort(order, instId));
    }

    private void cancelOrderBestEffort(Order order, String instId) {
        try {
            integrationService.cancelOrder(order, instId);
        } catch (RuntimeException e) {
            log.warn("Kill-switch cancel-order best-effort failure orderId={}: {}", order.getId(), e.getMessage());
        }
    }

    private void cancelLiveAlgoOrders(Deal deal, String instId) {
        safe(deal.getAlgoOrders()).stream()
                .filter(algoOrder -> isTrue(algoOrder.isLive()))
                .forEach(algoOrder -> cancelAlgoBestEffort(algoOrder, instId));
    }

    private void cancelAlgoBestEffort(AlgoOrder algoOrder, String instId) {
        try {
            integrationService.cancelAlgoOrder(algoOrder, instId);
        } catch (RuntimeException e) {
            log.warn("Kill-switch cancel-algo best-effort failure algoOrderId={}: {}", algoOrder.getId(), e.getMessage());
        }
    }

    private <T> List<T> safe(List<T> list) {
        return isNull(list) ? emptyList() : list;
    }
}
