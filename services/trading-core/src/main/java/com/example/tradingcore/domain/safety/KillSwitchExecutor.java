package com.example.tradingcore.domain.safety;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.config.KillSwitchProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Снимает живой риск сделки прямыми вызовами границы интеграции и
 * подтверждает снятие фактами (docs/components/KillSwitchExecutor.md).
 *
 * <p><b>Вне реестра команд:</b> по типу команды не диспетчеризуется,
 * зовётся программно. Аварийный тормоз доводит свой ход сам и не зависит
 * от того, жива ли петля.
 *
 * <p><b>Порядок хода — не удобство, а два инварианта сразу.</b> Живые ноги
 * траншей снимаются раньше экспозиции (docs/rules/exit-teardown-order.md):
 * не-reduce-only нога, исполнившаяся после закрытия, открыла бы позицию
 * заново. Защита снимается ПОСЛЕДНЕЙ и только после подтверждённого
 * закрытия позиции — живая позиция не оголяется ни на мгновение.
 *
 * <p><b>Защита снимается в обеих формах</b> — отдельной условной заявкой и
 * встроенной. Встроенная при непустом наливе родителя материализуется на
 * бирже самостоятельной заявкой и переживает терминал родителя
 * (docs/models/domain/core/Order.md §«Встроенная защита»), то есть
 * перечнем живых отдельных заявок не покрывается.
 *
 * <p><b>Преконтроль риска не вызывается</b> — это safety-тропа; статуса
 * блокировки исполнитель не ставит и сделку по FSM не ведёт.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KillSwitchExecutor {

    /** Предел попыток, когда конфигурация его не назвала. */
    private static final Integer DEFAULT_TEARDOWN_ATTEMPTS = 3;

    private final ExchangeOperationsClient exchangeOperationsClient;
    private final ServiceCommandExecutor serviceCommandExecutor;
    private final DealContextService dealContextService;
    private final KillSwitchProperties properties;

    /**
     * Снять живой риск сделки и подтвердить снятие фактами.
     *
     * <p>Не подтверждено — ход повторяется, ограниченно. Предел исчерпан —
     * отказ; эскалацию радиуса держит координатор последовательности, и
     * автоматический повтор предела не обходит.
     */
    public ServiceCommandExecutionResult execute(DealContext dealContext) {
        Integer maxAttempts = teardownAttempts();
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            teardown(dealContext);
            if (isTrue(confirmedFlat(dealContext))) {
                return ServiceCommandExecutionResult.ok();
            }
            log.warn("Kill-switch teardown is not confirmed flat instId={} attempt={}/{}",
                    externalInstrumentId, attempt, maxAttempts);
        }
        return ServiceCommandExecutionResult.failure(RuntimeErrorCode.EXCHANGE_ERROR,
                "Kill-switch could not confirm flat instId=" + externalInstrumentId
                        + " after " + maxAttempts + " attempts");
    }

    /**
     * Один ход снятия риска: ноги → экспозиция → подтверждение закрытия →
     * защита. Каждый вызов границы best-effort: отказ одного не отменяет
     * остальных, потому что реакция снимает риск, а не отчитывается о нём.
     */
    private void teardown(DealContext dealContext) {
        cancelLiveLegs(dealContext);
        closePositionIfLive(dealContext);
        confirmPositionSafely(dealContext);
        if (isFalse(dealContext.getDeal().hasLivePositionRisk())) {
            cancelProtection(dealContext);
        }
    }

    /**
     * Живые ноги траншей — первыми. Ноги берутся обходом траншей:
     * донорского поля агрегата ядро не читает
     * (docs/models/domain/aggregate/Deal.md §Структура).
     */
    private void cancelLiveLegs(DealContext dealContext) {
        String accountInternalId = accountInternalId(dealContext);
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        for (Order leg : liveLegs(dealContext.getDeal())) {
            callSafely("cancel-order", leg.getId(),
                    () -> exchangeOperationsClient.cancelOrder(accountInternalId, leg, externalInstrumentId));
        }
    }

    private void closePositionIfLive(DealContext dealContext) {
        if (isFalse(dealContext.getDeal().hasLivePositionRisk())) {
            return;
        }
        Instrument instrument = dealContext.getInstrument();
        callSafely("close-position", dealContext.getDeal().getId(),
                () -> exchangeOperationsClient.closePosition(accountInternalId(dealContext),
                        instrument.getExternalId(), instrument.getExternalSettlementCurrency()));
    }

    /**
     * Защита обеих форм — последней. Ход выполняется только на
     * подтверждённом отсутствии живой экспозиции: снятая раньше, защита
     * оставила бы позицию без покрытия на время её закрытия.
     */
    private void cancelProtection(DealContext dealContext) {
        String accountInternalId = accountInternalId(dealContext);
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        for (AlgoOrder algoOrder : liveAlgoOrders(dealContext.getDeal())) {
            callSafely("cancel-algo-order", algoOrder.getId(), () -> exchangeOperationsClient
                    .cancelAlgoOrder(accountInternalId, algoOrder, externalInstrumentId));
        }
        for (AttachedAlgoOrder protection : liveAttachedProtections(dealContext.getDeal())) {
            callSafely("cancel-attached-protection", protection.getId(), () -> exchangeOperationsClient
                    .cancelAttachedProtection(accountInternalId, protection, externalInstrumentId));
        }
    }

    /**
     * Подтверждение закрытия экспозиции: добыча эпизода через диспетчер и
     * перечитка графа. Ответ площадки истиной не служит
     * (docs/rules/ack-not-runtime-truth.md), поэтому спрашивается факт.
     */
    private void confirmPositionSafely(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        runSafely("refresh-position", () -> {
            serviceCommandExecutor.execute(refreshPositionCommand(deal), dealContext);
            dealContextService.reloadRuntimeGraph(deal);
        });
    }

    /**
     * Живого риска не осталось ни в одном носителе: экспозиция, ноги,
     * отдельные условные заявки и встроенные защиты.
     *
     * <p><b>Судьбу встроенной защиты резолвит добыча РОДИТЕЛЬСКОЙ
     * заявки</b>, поэтому перечень добычи собирается по родителям живых
     * защит, а не по живым заявкам: родитель к этому моменту чаще всего
     * терминален и в перечень живых не попадает вовсе.
     *
     * <p>Не добытые факты подтверждением не считаются: транзиентный отказ
     * чтения даёт «не flat» этой попыткой, а не срыв ограниченного цикла.
     */
    private Boolean confirmedFlat(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        Boolean factsFetched = runSafely("flat-confirm", () -> {
            refreshFacts(dealContext);
            dealContextService.reloadRuntimeGraph(deal);
        });
        return isTrue(factsFetched) && isTrue(isFlat(deal));
    }

    private void refreshFacts(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        serviceCommandExecutor.execute(refreshPositionCommand(deal), dealContext);
        for (Order leg : liveLegs(deal)) {
            serviceCommandExecutor.execute(refreshOrderCommand(deal, leg.getId()), dealContext);
        }
        for (AlgoOrder algoOrder : liveAlgoOrders(deal)) {
            serviceCommandExecutor.execute(refreshAlgoOrderCommand(deal, algoOrder.getId()), dealContext);
        }
        for (Long parentId : parentsOfLiveProtections(deal)) {
            serviceCommandExecutor.execute(refreshOrderCommand(deal, parentId), dealContext);
        }
    }

    private Boolean isFlat(Deal deal) {
        return isFalse(deal.hasLivePositionRisk())
                && isEmpty(liveLegs(deal))
                && isEmpty(liveAlgoOrders(deal))
                && isEmpty(liveAttachedProtections(deal));
    }

    private static List<Order> liveLegs(Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> tranche.liveOrders().stream())
                .collect(Collectors.toList());
    }

    private static List<AlgoOrder> liveAlgoOrders(Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> tranche.liveAlgoOrders().stream())
                .collect(Collectors.toList());
    }

    private static List<AttachedAlgoOrder> liveAttachedProtections(Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> tranche.liveAttachedProtections().stream())
                .collect(Collectors.toList());
    }

    /** Заявки, несущие живую встроенную защиту, без дублей. */
    private static Set<Long> parentsOfLiveProtections(Deal deal) {
        return liveAttachedProtections(deal).stream()
                .map(AttachedAlgoOrder::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private ServiceCommand refreshPositionCommand(Deal deal) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_POSITION_COMMAND)
                .dealId(deal.getId())
                .build();
    }

    private ServiceCommand refreshOrderCommand(Deal deal, Long orderId) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ORDER_COMMAND)
                .dealId(deal.getId())
                .payload(new RefreshOrderCommandPayload(orderId))
                .build();
    }

    private ServiceCommand refreshAlgoOrderCommand(Deal deal, Long algoOrderId) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND)
                .dealId(deal.getId())
                .payload(new RefreshAlgoOrderCommandPayload(algoOrderId))
                .build();
    }

    /**
     * Вызов границы best-effort. Отказ одного снятия не отменяет
     * остальных: подтверждение приносят факты, а не ответы, и
     * недоснятое поймает следующая попытка.
     */
    private void callSafely(String operation, Long subjectId, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            log.warn("Kill-switch {} best-effort failure subjectId={}: {}", operation, subjectId,
                    e.getMessage());
        }
    }

    /**
     * Ход добычи best-effort; {@code false} — факты этой попыткой не
     * добыты, то есть подтверждать нечем.
     */
    private Boolean runSafely(String operation, Runnable call) {
        try {
            call.run();
            return true;
        } catch (RuntimeException e) {
            log.warn("Kill-switch {} failed: {}", operation, e.getMessage());
            return false;
        }
    }

    private static String accountInternalId(DealContext dealContext) {
        return dealContext.getExchangeAccount().getInternalId();
    }

    private Integer teardownAttempts() {
        Integer maxAttempts = properties.getMaxTeardownAttempts();
        return nonNull(maxAttempts) && maxAttempts > 0 ? maxAttempts : DEFAULT_TEARDOWN_ATTEMPTS;
    }
}
