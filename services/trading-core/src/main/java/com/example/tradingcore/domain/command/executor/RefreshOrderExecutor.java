package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingcore.domain.command.resolve.AttachedProtectionFacts;
import com.example.tradingcore.domain.command.resolve.AttachedProtectionResolution;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.internal.api.exchange.ControlledExchangeException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.integration.internal.api.exchange.ExternalNotFoundException;
import com.example.tradingcore.integration.internal.api.exchange.ExternalStatusException;
import com.example.tradingcore.mapping.OrderMapper;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.util.Constants;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Добыча состояния обычной заявки. Циклов ДВА, и оба идут внутри одной
 * команды (docs/components/RefreshOrderExecutor.md):
 *
 * <pre>
 * цикл 1 — заявка: по идентификатору → ожидающие → история
 *          исчерпан ⇒ терминал «не найдена после добычи»
 * цикл 2 — материализованная встроенная защита, только у ТЕРМИНАЛЬНОГО
 *          родителя: живые условные по инструменту → разбор истории
 * </pre>
 *
 * <p><b>Второй цикл — не удобство.</b> Встроенная защита, развёрнутая
 * источником в самостоятельную живую условную заявку, не находится ни
 * одной ногой первого цикла: без второго цикла она читалась бы снятой,
 * покрытие недосчитывалось бы, и штатная сделка уходила бы в биржевую
 * ступень 2 по ложному факту — ошибка в разрешающую сторону.
 *
 * <p><b>Гейт запуска второго цикла — терминальность родителя</b>, а не
 * исход первой ступени: у живого родителя защита ещё в его теле,
 * материализовать её нечему, и исчерпание цикла давало бы потерянное
 * покрытие на живой защите. Сам гейт живёт у резолвера — своей копии
 * здесь нет.
 *
 * <p><b>Метрики исполнения приходят готовыми той же добычей</b> —
 * накопленный налив, средняя цена, комиссия; отдельной команды по сделкам
 * исполнения нет.
 *
 * <p><b>Отметку исхода транзакция сохраняет, хотя звено и бросает:</b>
 * контролируемое исключение изъято из отката, иначе обработчик прохода
 * поднял бы биржевую ступень по факту, которого в базе нет
 * (docs/components/ServiceCommandExecutor.md).
 */
@Component
@RequiredArgsConstructor
public class RefreshOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final ExchangeOperationsClient exchangeOperationsClient;
    private final OrderMapper orderMapper;
    private final AttachedAlgoOrderStateResolver attachedStateResolver;
    private final DealRiskNumbersService dealRiskNumbersService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_ORDER_COMMAND;
    }

    @Override
    @Transactional(noRollbackFor = ControlledExchangeException.class)
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        RefreshOrderCommandPayload payload = (RefreshOrderCommandPayload) command.getPayload();
        Order order = target(payload.getOrderId(), dealContext);
        Order fetched = fetchOrFail(order, dealContext);
        orderMapper.updateFromFetched(fetched, order);
        applyStatus(order, fetched);
        HoldSignal requestedRung = resolveAttached(order, fetched, dealContext);
        orderDataService.save(order);
        if (isFalse(dealRiskNumbersService.recompute(dealContext))) {
            return ServiceCommandExecutionResult.notCompleted(
                    "Deal graph incomplete: risk numbers not recomputed for order " + order.getId());
        }
        completeAction(actionState);
        return isNull(requestedRung)
                ? ServiceCommandExecutionResult.ok()
                : ServiceCommandExecutionResult.okWithHold(requestedRung);
    }

    /**
     * Цель звена — нога ИЗ ГРАФА прохода, а при её отсутствии durable-
     * чтение. Ход несущий: числа риска считаются по графу, и правка
     * отдельной копии осталась бы им не видна — заявленный и взятый риск
     * посчитались бы по прежнему наливу.
     */
    private Order target(Long orderId, DealContext dealContext) {
        return emptyIfNull(dealContext.getDeal().getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.getOrders()).stream())
                .filter(item -> Objects.equals(orderId, item.getId()))
                .findFirst()
                .orElseGet(() -> orderDataService.getRequiredById(orderId));
    }

    /**
     * Цикл 1; исчерпан без находки — терминал и бросок. Пустой ответ
     * одного источника основанием не является
     * (docs/rules/controlled-exchange-exceptions.md).
     */
    private Order fetchOrFail(Order order, DealContext dealContext) {
        Order fetched;
        try {
            fetched = findFetched(order, dealContext);
        } catch (ExternalStatusException e) {
            failWith(order, toCloseReason(e.getReasonCode()));
            throw e;
        }
        if (isNull(fetched)) {
            failWith(order, Order.CloseReason.MISSING_AFTER_REFRESH);
            throw new ExternalNotFoundException(
                    "Order not found after full evidence cycle: " + order.getInternalId());
        }
        return fetched;
    }

    /** По идентификатору → ожидающие → история; обрыв на первом нашедшем. */
    private Order findFetched(Order order, DealContext dealContext) {
        String accountInternalId = dealContext.getExchangeAccount().getInternalId();
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        Order single = exchangeOperationsClient.getOrder(accountInternalId, externalInstrumentId,
                order.getExternalId(), order.getInternalId());
        if (nonNull(single)) {
            return single;
        }
        Order pending = matchByInternalId(
                exchangeOperationsClient.getPendingOrders(accountInternalId, externalInstrumentId),
                order.getInternalId());
        if (nonNull(pending)) {
            return pending;
        }
        return matchByInternalId(
                exchangeOperationsClient.getOrderHistory(accountInternalId, externalInstrumentId),
                order.getInternalId());
    }

    /** Матч по КЛИЕНТСКОМУ идентификатору: биржевого у ненайденной записи может не быть вовсе. */
    private Order matchByInternalId(List<Order> candidates, String internalId) {
        if (isEmpty(candidates)) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> isNotBlank(candidate.getInternalId())
                        && Objects.equals(internalId, candidate.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Доменный статус приезжает готовым с добытой ноги; здесь он
     * применяется доменным переходом, а тот держит write-once причины.
     *
     * <p><b>Причина отмены берётся из НАШЕГО намерения</b>, а не из статуса
     * источника; намерения нет — ставится {@code UNKNOWN}: ребро в
     * отменённое требует непустой причины, и пустота сделала бы отмену,
     * инициированную биржей, непроходимой
     * (docs/rules/external-status-resolution.md).
     *
     * <p>Подтверждение прежнего статуса переходом не является: наблюдение
     * живой заявки идёт каждым тиком и состояния не двигает.
     */
    private void applyStatus(Order order, Order fetched) {
        Order.Status observed = fetched.getStatus();
        if (Objects.equals(observed, order.getStatus())) {
            return;
        }
        switch (observed) {
            case COMPLETED -> order.toComplete();
            case CANCELED -> order.toCancel(nonNull(order.getCloseReason())
                    ? order.getCloseReason()
                    : Order.CloseReason.UNKNOWN);
            case ACTIVE, PARTIALLY_COMPLETED -> order.setStatus(observed);
            default -> throw new IllegalStateException("Unobservable order status from source: " + observed);
        }
    }

    /**
     * Резолв состояния встроенной защиты по фактам. Факты первой ступени —
     * предъявление в теле добытого родителя, его статус и налив; факты
     * цикла 2 (живая запись, нога разбора) добываются здесь же, но
     * <b>только у терминального родителя</b>: у живого защита ещё в его
     * теле, и лишние вызовы источника были бы платой ни за что.
     */
    private HoldSignal resolveAttached(Order order, Order fetched, DealContext dealContext) {
        if (isEmpty(order.getAttachedAlgoOrders())) {
            return null;
        }
        DealTranche tranche = trancheOf(order, dealContext);
        HoldSignal requested = null;
        for (AttachedAlgoOrder attached : order.getAttachedAlgoOrders()) {
            HoldSignal signal = resolveOne(attached, order, fetched, tranche, dealContext);
            if (nonNull(signal)) {
                requested = signal;
            }
        }
        return requested;
    }

    private HoldSignal resolveOne(AttachedAlgoOrder attached, Order order, Order fetched, DealTranche tranche,
                                  DealContext dealContext) {
        String accountInternalId = dealContext.getExchangeAccount().getInternalId();
        String externalInstrumentId = dealContext.getInstrument().getExternalId();
        AttachedAlgoOrder parentBody = matchProtection(fetched.getAttachedAlgoOrders(), attached.getInternalId());
        boolean searchCycle = isTrue(attachedStateResolver.runsSearchCycle(order.getStatus(),
                order.getAccumulatedFillSize()));
        AttachedAlgoOrder live = searchCycle
                ? matchProtection(exchangeOperationsClient.getPendingMaterializedProtections(accountInternalId,
                        externalInstrumentId), attached.getInternalId())
                : null;
        ProtectionHistoryLeg leg = isNull(live) && searchCycle
                ? findInHistory(attached.getInternalId(), accountInternalId, externalInstrumentId, tranche)
                : null;
        AttachedProtectionFacts facts = AttachedProtectionFacts.builder()
                .observed(isNull(live) ? parentBody : live)
                .parentStatus(order.getStatus())
                .parentAccumulatedFillSize(order.getAccumulatedFillSize())
                .standaloneRecordFound(nonNull(live))
                .trancheExposure(isNull(tranche) ? null : tranche.exposure())
                .standaloneProtectionExists(nonNull(tranche) && isTrue(tranche.hasStandaloneProtection()))
                .historyLegFound(leg)
                .cancelIntentStanding(nonNull(attached.getCloseReason()))
                .build();
        AttachedProtectionResolution resolution = attachedStateResolver.resolve(facts);
        applyResolution(attached, resolution);
        return emptyAnalysisRung(resolution, searchCycle, leg);
    }

    /**
     * Пустой разбор истории — не факт о защите, а ОТСУТСТВИЕ факта:
     * терминал не ставится, а проход поднимает сигнал, и разбор ведёт
     * человек (docs/lifecycles/Order.md §«Пустой разбор истории»).
     *
     * <p><b>Ступень ЗАТРЕБУЕТСЯ, а поднимает её проход:</b> служба ступеней
     * ведёт снятие риска тем же диспетчером, который зовёт это звено, и
     * прямая зависимость замкнулась бы в цикл.
     *
     * <p><b>Ступень мягкая.</b> Ветвь разбора достижима, только когда риск
     * транша либо отсутствует, либо покрыт ОТДЕЛЬНОЙ защитой: принятый
     * риск покрыт, рвать его нечем, и жёсткая форма была бы платой
     * рыночной цены без основания (docs/rules/instrument-hold.md).
     */
    private HoldSignal emptyAnalysisRung(AttachedProtectionResolution resolution, boolean searchCycle,
                                         ProtectionHistoryLeg leg) {
        if (isFalse(searchCycle) || nonNull(leg) || isTrue(resolution.hasStatus())) {
            return null;
        }
        return HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_PROTECTION_FATE_UNKNOWN);
    }

    /**
     * Ноги разбора истории — по одной на терминальное состояние контракта;
     * обрыв на первой нашедшей.
     *
     * <p>Разбор идёт только на ветви, где покрытие ещё может быть
     * объяснено: если транш несёт живую экспозицию и отдельной защиты у
     * него нет, вторая ступень терминализует защиту потерянной, и любой
     * факт истории этого не меняет — опрашивать её значило бы платить
     * источнику за ответ, на который решение не смотрит.
     */
    private ProtectionHistoryLeg findInHistory(String internalId, String accountInternalId,
                                               String externalInstrumentId, DealTranche tranche) {
        if (analysisSkipped(tranche)) {
            return null;
        }
        for (ProtectionHistoryLeg leg : ProtectionHistoryLeg.values()) {
            if (nonNull(matchProtection(exchangeOperationsClient.getMaterializedProtectionHistory(
                    accountInternalId, externalInstrumentId, leg), internalId))) {
                return leg;
            }
        }
        return null;
    }

    /** Ветвь потерянного покрытия: разбор истории на ней не запускается. */
    private boolean analysisSkipped(DealTranche tranche) {
        return nonNull(tranche)
                && tranche.exposure().signum() > 0
                && isFalse(tranche.hasStandaloneProtection());
    }

    /** Транш заявки из графа прохода; пусто — заявка транша не несёт. */
    private DealTranche trancheOf(Order order, DealContext dealContext) {
        if (isNull(order.getDealTrancheId())) {
            return null;
        }
        return emptyIfNull(dealContext.getDeal().getTranches()).stream()
                .filter(item -> Objects.equals(order.getDealTrancheId(), item.getId()))
                .findFirst()
                .orElse(null);
    }

    /** Совпадение по КЛИЕНТСКОМУ идентификатору — единственный сходящийся операнд связи. */
    private AttachedAlgoOrder matchProtection(List<AttachedAlgoOrder> records, String internalId) {
        if (isEmpty(records)) {
            return null;
        }
        return records.stream()
                .filter(record -> isNotBlank(record.getInternalId())
                        && Objects.equals(internalId, record.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * «Исход не определён» статуса не двигает и причины не пишет; сигнал
     * поднимает вызывающий проход.
     *
     * <p>Подтверждение прежнего состояния переходом не является: граф
     * переходов защиты петель не содержит, а наблюдение живой защиты идёт
     * каждым тиком.
     *
     * <p><b>Терминал идёт особым ходом</b>, потому что защита, ещё
     * стоящая неподтверждённой, активируется тем же наблюдением: ребра
     * «неподтверждена → сработала» в матрице нет намеренно, и без
     * промежуточной активации терминал по найденному факту применить было
     * бы нечем (docs/lifecycles/Order.md §«Разбор истории»).
     */
    private void applyResolution(AttachedAlgoOrder attached, AttachedProtectionResolution resolution) {
        if (isFalse(resolution.hasStatus()) || Objects.equals(resolution.getStatus(), attached.getStatus())) {
            return;
        }
        switch (resolution.getStatus()) {
            case PENDING -> attached.toPending();
            case ACTIVE -> attached.toActive();
            default -> attached.applyObservedTerminal(resolution.getStatus(), resolution.getCloseReason());
        }
    }

    /** Ошибочное состояние с причиной; причина write-once — ранее стоящая не перетирается. */
    private void failWith(Order order, Order.CloseReason closeReason) {
        order.toError(closeReason);
        orderDataService.save(order);
    }

    private Order.CloseReason toCloseReason(ExternalStatusReason reason) {
        return ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS.equals(reason)
                ? Order.CloseReason.UNKNOWN_EXTERNAL_STATUS
                : Order.CloseReason.UNKNOWN;
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
