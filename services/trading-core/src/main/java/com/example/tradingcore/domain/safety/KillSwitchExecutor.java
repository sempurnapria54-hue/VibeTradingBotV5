package com.example.tradingcore.domain.safety;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.KillSwitchProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.CancelAlgoOrderExecutor;
import com.example.tradingcore.domain.command.executor.CancelAttachedProtectionExecutor;
import com.example.tradingcore.domain.command.executor.CancelOrderExecutor;
import com.example.tradingcore.domain.command.executor.ClosePositionExecutor;
import com.example.tradingcore.domain.command.executor.CommandExecutor;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CancelAttachedProtectionCommandPayload;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Снимает живой риск сделки исполнителями команд снятия и
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
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final InstrumentDataService instrumentDataService;
    private final KillSwitchProperties properties;
    private final CancelOrderExecutor cancelOrderExecutor;
    private final ClosePositionExecutor closePositionExecutor;
    private final CancelAlgoOrderExecutor cancelAlgoOrderExecutor;
    private final CancelAttachedProtectionExecutor cancelAttachedProtectionExecutor;

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
     * Снять живой риск радиуса вне графа сделок и подтвердить радиус
     * позициями площадки (docs/components/KillSwitchExecutor.md §«Риск вне
     * графа сделок»).
     *
     * <p><b>Закрывается только позиция на инструменте без нетерминальной
     * сделки.</b> Позицию сделки снимает ход сделки: ноги траншей там
     * снимаются раньше экспозиции, и закрытие её здесь, мимо ног, открыло бы
     * позицию заново их исполнением.
     *
     * <p><b>Подтверждает радиус ЛЮБАЯ живая позиция</b>, а не только
     * закрываемая: остаток на инструменте сделки значит, что ход сделки
     * риска не снял, как бы он о себе ни отчитался. Не добытые позиции
     * подтверждением не считаются.
     *
     * @param externalInstrumentId инструмент радиуса пары; пусто — радиус
     *                             счёта целиком
     * @param population           нетерминальные сделки радиуса
     */
    public Boolean closePositionsOutsideDeals(Long exchangeAccountId, String externalInstrumentId,
                                              List<Deal> population) {
        ExchangeAccount account = exchangeAccountDataService.getRequiredById(exchangeAccountId);
        Set<String> dealInstruments = instrumentDataService.findExternalIdsByIds(population.stream()
                .map(Deal::getInstrumentId)
                .collect(Collectors.toSet()));
        Integer maxAttempts = teardownAttempts();
        List<Position> live = livePositionsOnScope(account, externalInstrumentId);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (nonNull(live) && isEmpty(live)) {
                return true;
            }
            if (nonNull(live)) {
                live.stream()
                        .filter(position -> isFalse(dealInstruments.contains(position.getExternalInstrumentId())))
                        .forEach(position -> closeOutsideDeal(account, position));
            }
            live = livePositionsOnScope(account, externalInstrumentId);
            log.warn("Kill-switch scope is not confirmed flat by positions exchangeAccountId={} instId={}"
                    + " attempt={}/{}", exchangeAccountId, externalInstrumentId, attempt, maxAttempts);
        }
        return nonNull(live) && isEmpty(live);
    }

    /**
     * Позиции радиуса с ненулевым размером; пусто — позиции этой попыткой
     * не добыты. Читается срез счёта целиком и сужается инструментом: одно
     * чтение на оба радиуса.
     */
    private List<Position> livePositionsOnScope(ExchangeAccount account, String externalInstrumentId) {
        try {
            return emptyIfNull(exchangeOperationsClient.getPositions(account.getInternalId())).stream()
                    .filter(position -> isTrue(position.hasLiveSize()))
                    .filter(position -> isNull(externalInstrumentId)
                            || Objects.equals(externalInstrumentId, position.getExternalInstrumentId()))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            log.warn("Kill-switch positions read failed exchangeAccountId={}: {}", account.getId(),
                    e.getMessage());
            return null;
        }
    }

    /**
     * Закрытие позиции без сделки. Строка позиции валюты расчёта не несёт;
     * у инструмента проекции она читается, у инструмента вне контура её нет,
     * и закрытие уходит без неё — контракт закрытия её не требует.
     */
    private void closeOutsideDeal(ExchangeAccount account, Position position) {
        String externalInstrumentId = position.getExternalInstrumentId();
        String settleCurrency = instrumentDataService.findSettlementCurrency(account.getExchangeCode(),
                externalInstrumentId).orElse(null);
        callSafely("close-position-outside-deal", account.getId(), () -> exchangeOperationsClient
                .closePosition(account.getInternalId(), externalInstrumentId, settleCurrency));
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
        Deal deal = dealContext.getDeal();
        for (Order leg : liveLegs(deal)) {
            dispatchSafely("cancel-order", leg.getId(), cancelOrderExecutor, dealContext, command(deal,
                    ServiceCommandType.CANCEL_ORDER_COMMAND,
                    new CancelOrderCommandPayload(leg.getId(), Order.CloseReason.KILL_SWITCH)));
        }
    }

    private void closePositionIfLive(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        Position live = deal.livePosition();
        if (isFalse(deal.hasLivePositionRisk()) || isNull(live)) {
            return;
        }
        dispatchSafely("close-position", deal.getId(), closePositionExecutor, dealContext, command(deal,
                ServiceCommandType.CLOSE_POSITION_COMMAND,
                new ClosePositionCommandPayload(live.getId(), Position.CloseReason.KILL_SWITCH)));
    }

    /**
     * Защита обеих форм — последней. Ход выполняется только на
     * подтверждённом отсутствии живой экспозиции: снятая раньше, защита
     * оставила бы позицию без покрытия на время её закрытия.
     */
    private void cancelProtection(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        for (AlgoOrder algoOrder : liveAlgoOrders(deal)) {
            dispatchSafely("cancel-algo-order", algoOrder.getId(), cancelAlgoOrderExecutor, dealContext, command(deal,
                    ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND,
                    new CancelAlgoOrderCommandPayload(algoOrder.getId(), AlgoOrder.CloseReason.KILL_SWITCH)));
        }
        for (AttachedAlgoOrder protection : liveAttachedProtections(deal)) {
            dispatchSafely("cancel-attached-protection", protection.getId(), cancelAttachedProtectionExecutor,
                    dealContext, command(deal,
                    ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND,
                    new CancelAttachedProtectionCommandPayload(protection.getId(),
                            AttachedAlgoOrder.CloseReason.KILL_SWITCH)));
        }
        // Намерение снятия записано в перечитанные сущности, а добыча
        // подтверждения читает граф прохода: без перечитки она терминализовала
        // бы защиту по графу без намерения — потерянной, а не снятой.
        runSafely("reload-after-protection-cancel", () -> dealContextService.reloadRuntimeGraph(deal));
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

    /**
     * Команда снятия без анкера: у аварийного хода строки исполнения нет, а
     * исполнитель команды пишет намерение снятия с причиной {@code KILL_SWITCH}
     * write-once — по нему добыча и терминализует сущность снятой, а не
     * потерянной (docs/components/KillSwitchExecutor.md §Порядок).
     */
    private ServiceCommand command(Deal deal, ServiceCommandType type, ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(deal.getId())
                .payload(payload)
                .build();
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
     * Снятие best-effort ИСПОЛНИТЕЛЕМ команды, мимо диспетчера: исполнитель
     * пишет намерение снятия, а учёт отказа без анкера — механизм прохода, у
     * которого повтор ведёт сам проход (docs/components/ServiceCommandExecutor.md
     * §«Отказ команды без анкера — происшествие, а не бюджет»); у аварийного
     * хода повтор и отчёт свои. Отказ одного снятия остальных не отменяет.
     */
    private void dispatchSafely(String operation, Long subjectId, CommandExecutor executor,
                                DealContext dealContext, ServiceCommand command) {
        callSafely(operation, subjectId, () -> {
            ServiceCommandExecutionResult result = executor.execute(command, null, dealContext);
            if (isFalse(result.getSuccess())) {
                log.warn("Kill-switch {} refused subjectId={}: {}", operation, subjectId, result.getMessage());
            }
        });
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

    private Integer teardownAttempts() {
        Integer maxAttempts = properties.getMaxTeardownAttempts();
        return nonNull(maxAttempts) && maxAttempts > 0 ? maxAttempts : DEFAULT_TEARDOWN_ATTEMPTS;
    }
}
