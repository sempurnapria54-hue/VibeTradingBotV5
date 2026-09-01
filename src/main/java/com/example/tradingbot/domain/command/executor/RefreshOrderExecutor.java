package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingbot.domain.command.resolve.AttachedProtectionFacts;
import com.example.tradingbot.domain.command.resolve.AttachedProtectionResolution;
import com.example.tradingbot.domain.command.resolve.OrderExternalStatusResolver;
import com.example.tradingbot.domain.command.resolve.ProtectionHistoryLeg;
import com.example.tradingbot.domain.command.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.integration.service.ExternalStatusException;
import com.example.tradingbot.integration.service.ExternalStatusReason;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.Constants;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет REFRESH_ORDER, обходя order evidence-cycle внутри команды:
 * GET /trade/order → orders-pending → orders-history, обрыв на первом
 * найденном (матч по internalId/clOrdId). Не найден после полного цикла
 * → Order.ERROR + MISSING_AFTER_REFRESH (пустой ответ одного эндпоинта —
 * не основание). Найден → обновляет поля, применяет статус через
 * OrderExternalStatusResolver (unknown status → ERROR), резолвит attached
 * protection. ACK не использует. См.
 * docs/components/RefreshOrderExecutor.md,
 * docs/decisions/refresh-evidence-cycle-ownership.md.
 */
@Component
@RequiredArgsConstructor
public class RefreshOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;
    private final OrderMapper orderMapper;
    private final OrderExternalStatusResolver orderStatusResolver;
    private final AttachedAlgoOrderStateResolver attachedStateResolver;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        RefreshOrderCommandPayload payload = (RefreshOrderCommandPayload) command.getPayload();
        Order order = orderDataService.getRequiredById(payload.getOrderId());
        OrderExternalSnapshot snapshot = findSnapshot(order, dealContext.getInstrument().getExternalId());
        HoldSignal requestedRung = null;
        if (isNull(snapshot)) {
            order.toError(Order.CloseReason.MISSING_AFTER_REFRESH);
        } else {
            requestedRung = applyOrUnknown(order, snapshot, dealContext);
        }
        orderDataService.save(order);
        completeAction(actionState);
        return isNull(requestedRung)
                ? ServiceCommandExecutionResult.ok()
                : ServiceCommandExecutionResult.okWithHold(requestedRung);
    }

    /** Order evidence-cycle: single → pending → history; обрыв на первом найденном. */
    private OrderExternalSnapshot findSnapshot(Order order, String instId) {
        OrderExternalSnapshot single = integrationService.getOrder(instId, order.getExternalId(), order.getInternalId());
        if (nonNull(single)) {
            return single;
        }
        OrderExternalSnapshot pending = matchByInternalId(integrationService.getPendingOrders(instId),
                order.getInternalId());
        if (nonNull(pending)) {
            return pending;
        }
        return matchByInternalId(integrationService.getOrderHistory(instId), order.getInternalId());
    }

    private OrderExternalSnapshot matchByInternalId(List<OrderExternalSnapshot> snapshots, String internalId) {
        if (isEmpty(snapshots)) {
            return null;
        }
        return snapshots.stream()
                .filter(snapshot -> isNotBlank(snapshot.getInternalId())
                        && Objects.equals(internalId, snapshot.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    private HoldSignal applyOrUnknown(Order order, OrderExternalSnapshot snapshot, DealContext dealContext) {
        orderMapper.updateFromSnapshot(snapshot, order);
        try {
            applyStatus(order, snapshot);
            return resolveAttached(order, snapshot, dealContext);
        } catch (ExternalStatusException e) {
            order.toError(toCloseReason(e.getReasonCode()));
            return null;
        }
    }

    private void applyStatus(Order order, OrderExternalSnapshot snapshot) {
        StatusResolveResult<Order.Status, Order.CloseReason> result =
                orderStatusResolver.resolve(snapshot.getExternalStatus());
        order.setStatus(result.getStatus());
        if (isNull(order.getCloseReason()) && nonNull(result.getCloseReason())) {
            order.setCloseReason(result.getCloseReason());
        }
    }

    /**
     * Резолв состояния встроенной защиты по фактам. Факты первой ступени —
     * снапшот из тела родителя, статус и налив; факты цикла 2 (живая
     * запись, нога разбора) добываются здесь же, но <b>только у
     * терминального родителя</b>: у живого защита ещё в его теле,
     * материализовать её нечему, и лишние вызовы источника были бы платой
     * ни за что.
     */
    private HoldSignal resolveAttached(Order order, OrderExternalSnapshot snapshot, DealContext dealContext) {
        if (isEmpty(order.getAttachedAlgoOrders())) {
            return null;
        }
        DealTranche tranche = trancheOf(order, dealContext);
        HoldSignal requested = null;
        for (AttachedAlgoOrder attached : order.getAttachedAlgoOrders()) {
            HoldSignal signal = resolveOne(attached, order, snapshot, tranche, dealContext);
            if (nonNull(signal)) {
                requested = signal;
            }
        }
        return requested;
    }

    private HoldSignal resolveOne(AttachedAlgoOrder attached, Order order, OrderExternalSnapshot snapshot,
                                  DealTranche tranche, DealContext dealContext) {
        String instId = dealContext.getInstrument().getExternalId();
        AttachedAlgoOrderExternalSnapshot parentBody = matchAttached(snapshot, attached.getInternalId());
        boolean searchCycle = isTrue(attachedStateResolver.runsSearchCycle(order.getStatus(),
                order.getAccumulatedFillSize()));
        AttachedAlgoOrderExternalSnapshot live = searchCycle
                ? matchProtection(integrationService.getPendingMaterializedProtections(instId),
                        attached.getInternalId())
                : null;
        ProtectionHistoryLeg leg = isNull(live) && searchCycle
                ? findInHistory(attached.getInternalId(), instId, tranche)
                : null;
        AttachedProtectionFacts facts = AttachedProtectionFacts.builder()
                .snapshot(isNull(live) ? parentBody : live)
                .parentStatus(order.getStatus())
                .parentAccumulatedFillSize(order.getAccumulatedFillSize())
                .standaloneRecordFound(nonNull(live))
                .trancheExposure(isNull(tranche) ? null : tranche.exposure())
                .standaloneProtectionExists(isNull(tranche) ? false : tranche.hasStandaloneProtection())
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
     * <p><b>Ступень ЗАТРЕБУЕТСЯ, а поднимает её проход</b> — исполнитель
     * блокировки ведёт снятие риска тем же диспетчером команд, который
     * зовёт это звено, и прямая зависимость замкнулась бы в цикл.
     *
     * <p><b>Ступень мягкая.</b> Ветвь разбора достижима, только когда риск
     * транша либо отсутствует, либо покрыт ОТДЕЛЬНОЙ защитой: принятый
     * риск покрыт, рвать его нечем, и жёсткая форма была бы платой
     * рыночной цены без основания (docs/rules/instrument-hold.md).
     * Неизвестна судьба записи — поэтому новые входы по инструменту
     * запрещаются до разбора.
     */
    private HoldSignal emptyAnalysisRung(AttachedProtectionResolution resolution, boolean searchCycle,
                                         ProtectionHistoryLeg leg) {
        if (isFalse(searchCycle) || nonNull(leg) || isTrue(resolution.hasStatus())) {
            return null;
        }
        return HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_PROTECTION_FATE_UNKNOWN);
    }

    /**
     * Ноги разбора истории — по одной на терминальное состояние контракта
     * эндпоинта; обрыв на первой нашедшей.
     *
     * <p>Разбор идёт <b>только на ветви ANALYSE_HISTORY</b>: если транш
     * несёт живую экспозицию и отдельной защиты у него нет, вторая ступень
     * терминализует защиту потерянной, и любой факт истории этого не
     * меняет — опрашивать её значило бы платить источнику за ответ, на
     * который решение не смотрит.
     */
    private ProtectionHistoryLeg findInHistory(String internalId, String instId, DealTranche tranche) {
        if (analysisSkipped(tranche)) {
            return null;
        }
        for (ProtectionHistoryLeg leg : ProtectionHistoryLeg.values()) {
            if (nonNull(matchProtection(integrationService.getMaterializedProtectionHistory(instId, leg),
                    internalId))) {
                return leg;
            }
        }
        return null;
    }

    /** Ветвь PROTECTION_LOST второй ступени: разбор истории на ней не запускается. */
    private boolean analysisSkipped(DealTranche tranche) {
        return nonNull(tranche)
                && tranche.exposure().signum() > 0
                && isFalse(tranche.hasStandaloneProtection());
    }

    /** Транш заявки из контекста прохода; пусто — заявка транша не несёт. */
    private DealTranche trancheOf(Order order, DealContext dealContext) {
        if (isNull(order.getDealTrancheId())) {
            return null;
        }
        return dealContext.getDeal().getTranches().stream()
                .filter(item -> Objects.equals(order.getDealTrancheId(), item.getId()))
                .findFirst()
                .orElse(null);
    }

    /** Совпадение по КЛИЕНТСКОМУ идентификатору — единственный сходящийся операнд связи. */
    private AttachedAlgoOrderExternalSnapshot matchProtection(List<AttachedAlgoOrderExternalSnapshot> records,
                                                              String internalId) {
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
     * (аномалия плюс холд инструмента) поднимает владелец цикла 2 —
     * достраивается тем же ходом, что и сам цикл.
     */
    private void applyResolution(AttachedAlgoOrder attached, AttachedProtectionResolution resolution) {
        if (isFalse(resolution.hasStatus())) {
            return;
        }
        attached.setStatus(resolution.getStatus());
        if (isNull(attached.getCloseReason()) && nonNull(resolution.getCloseReason())) {
            attached.setCloseReason(resolution.getCloseReason());
        }
    }

    private AttachedAlgoOrderExternalSnapshot matchAttached(OrderExternalSnapshot snapshot, String internalId) {
        if (isEmpty(snapshot.getAttachedAlgoOrders())) {
            return null;
        }
        return snapshot.getAttachedAlgoOrders().stream()
                .filter(item -> isNotBlank(item.getInternalId()) && Objects.equals(internalId, item.getInternalId()))
                .findFirst()
                .orElse(null);
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
