package com.example.tradingbot.domain.service.validator;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.Instrument.Status;
import com.example.tradingbot.domain.model.kill_switch.KillSwitchResult;
import com.example.tradingbot.domain.model.kill_switch.StateSnapshot;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.service.anomaly.AnomalyService;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.domain.service.kill_switch.KillSwitchService;
import com.example.tradingbot.domain.service.kill_switch.reader.StateSnapshotReader;
import com.example.tradingbot.exception.TradeRuleViolationException;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ONE;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

@Service
@RequiredArgsConstructor
public class TradeRuleValidator {

    private static final String OVERHEAD_POSITIONS_COUNT = "OVERHEAD_POSITIONS_COUNT";
    private static final String POSITIONS_INVALID_INSTRUMENT_SCOPE = "POSITIONS_INVALID_INSTRUMENT_SCOPE";
    private static final String POSITIONS_IDENTITY_MISMATCH = "POSITIONS_IDENTITY_MISMATCH";
    private static final String REFRESH_PENDING_ORDERS_EXTERNAL_DUPLICATES = "REFRESH_PENDING_ORDERS_EXTERNAL_DUPLICATES";
    private static final String REFRESH_PENDING_ORDERS_INTERNAL_DUPLICATES = "REFRESH_PENDING_ORDERS_INTERNAL_DUPLICATES";
    private static final String REFRESH_PENDING_ORDERS_UNMATCHED_EXTERNAL = "REFRESH_PENDING_ORDERS_UNMATCHED_EXTERNAL";
    private static final String REFRESH_PENDING_ORDERS_INVALID_INSTRUMENT_SCOPE = "REFRESH_PENDING_ORDERS_INVALID_INSTRUMENT_SCOPE";
    private static final String REFRESH_ALGO_ORDERS_EXTERNAL_DUPLICATES = "REFRESH_ALGO_ORDERS_EXTERNAL_DUPLICATES";
    private static final String REFRESH_ALGO_ORDERS_INTERNAL_ALGO_DUPLICATES = "REFRESH_ALGO_ORDERS_INTERNAL_ALGO_DUPLICATES";
    private static final String REFRESH_ALGO_ORDERS_INTERNAL_ATTACHED_DUPLICATES = "REFRESH_ALGO_ORDERS_INTERNAL_ATTACHED_DUPLICATES";
    private static final String REFRESH_ALGO_ORDERS_UNMATCHED_EXTERNAL = "REFRESH_ALGO_ORDERS_UNMATCHED_EXTERNAL";
    private static final String REFRESH_ALGO_ORDERS_INVALID_INSTRUMENT_SCOPE = "REFRESH_ALGO_ORDERS_INVALID_INSTRUMENT_SCOPE";

    private final KillSwitchService killSwitchService;
    private final InstrumentService instrumentService;
    private final AnomalyService anomalyService;
    private final ClientManager clientManager;
    private final StateSnapshotReader stateSnapshotReader;
    private final JsonUtils jsonUtils;
    private final OrderDataService orderDataService;

    public void validatePositions(Exchange exchange,
                                  Instrument instrument,
                                  Long dealId,
                                  List<PositionExternalSnapshot> externalSnapshots,
                                  List<Position> domainPositions) {
        String violationCode = resolvePositionsViolationCode(instrument, externalSnapshots, domainPositions);
        if (Objects.isNull(violationCode)) {
            return;
        }

        executeTradeRuleViolationFlow(exchange,
                                      instrument,
                                      dealId,
                                      violationCode,
                                      Severity.CRITICAL);
        throw new TradeRuleViolationException(
                "Trade rule violation detected for positions: " + violationCode);
    }

    private String resolvePositionsViolationCode(Instrument instrument,
                                                 List<PositionExternalSnapshot> externalSnapshots,
                                                 List<Position> domainPositions) {
        if (hasOverheadPositions(externalSnapshots, domainPositions)) {
            return OVERHEAD_POSITIONS_COUNT;
        }
        if (hasExternalPositionsFromAnotherInstrument(instrument, externalSnapshots)) {
            return POSITIONS_INVALID_INSTRUMENT_SCOPE;
        }
        if (hasInternalPositionsFromAnotherInstrument(instrument, domainPositions)) {
            return POSITIONS_INVALID_INSTRUMENT_SCOPE;
        }
        if (hasPositionIdentityMismatch(externalSnapshots, domainPositions)) {
            return POSITIONS_IDENTITY_MISMATCH;
        }
        return null;
    }

    public void validateRefreshPendingOrders(Exchange exchange,
                                             Instrument instrument,
                                             Long dealId,
                                             List<OrderExternalSnapshot> externalPendingOrders,
                                             List<Order> internalLiveOrders) {
        String violationCode = resolveRefreshPendingOrdersViolationCode(instrument,
                                                                        externalPendingOrders,
                                                                        internalLiveOrders);
        if (Objects.isNull(violationCode)) {
            return;
        }

        executeTradeRuleViolationFlow(exchange,
                                      instrument,
                                      dealId,
                                      violationCode,
                                      Severity.CRITICAL);
        throw new TradeRuleViolationException(
                "Trade rule violation detected for refresh pending orders: " + violationCode);
    }

    public void validateRefreshAlgoOrders(Exchange exchange,
                                          Instrument instrument,
                                          Long dealId,
                                          List<AlgoOrderExternalSnapshot> externalLiveAlgoSnapshots,
                                          List<AlgoOrder> internalLiveAlgoOrders,
                                          List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        String violationCode = resolveRefreshAlgoOrdersViolationCode(instrument,
                                                                     externalLiveAlgoSnapshots,
                                                                     internalLiveAlgoOrders,
                                                                     internalAttachedAlgoOrders);
        if (Objects.isNull(violationCode)) {
            return;
        }

        executeTradeRuleViolationFlow(exchange,
                                      instrument,
                                      dealId,
                                      violationCode,
                                      Severity.CRITICAL);
        throw new TradeRuleViolationException(
                "Trade rule violation detected for refresh algo orders: " + violationCode);
    }

    private String resolveRefreshAlgoOrdersViolationCode(Instrument instrument,
                                                         List<AlgoOrderExternalSnapshot> externalLiveAlgoSnapshots,
                                                         List<AlgoOrder> internalLiveAlgoOrders,
                                                         List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        if (hasExternalAlgoDuplicates(externalLiveAlgoSnapshots)) {
            return REFRESH_ALGO_ORDERS_EXTERNAL_DUPLICATES;
        }

        if (hasInternalAlgoDuplicates(internalLiveAlgoOrders)) {
            return REFRESH_ALGO_ORDERS_INTERNAL_ALGO_DUPLICATES;
        }

        if (hasInternalAttachedDuplicates(internalAttachedAlgoOrders)) {
            return REFRESH_ALGO_ORDERS_INTERNAL_ATTACHED_DUPLICATES;
        }

        if (hasInternalAlgoOrdersFromAnotherInstrument(instrument, internalLiveAlgoOrders)) {
            return REFRESH_ALGO_ORDERS_INVALID_INSTRUMENT_SCOPE;
        }
        if (hasInternalAttachedOrdersFromAnotherInstrument(instrument, internalAttachedAlgoOrders)) {
            return REFRESH_ALGO_ORDERS_INVALID_INSTRUMENT_SCOPE;
        }

        if (hasUnmatchedOrAmbiguousExternalAlgoOrders(externalLiveAlgoSnapshots,
                                                      internalLiveAlgoOrders,
                                                      internalAttachedAlgoOrders)) {
            return REFRESH_ALGO_ORDERS_UNMATCHED_EXTERNAL;
        }

        return null;
    }

    private boolean hasExternalAlgoDuplicates(List<AlgoOrderExternalSnapshot> snapshots) {
        Map<String, Integer> byExternalId = new HashMap<>();
        Map<String, Integer> byInternalId = new HashMap<>();

        for (AlgoOrderExternalSnapshot snapshot : safeAlgoSnapshots(snapshots)) {
            if (incrementAndCheckDuplicate(byExternalId, snapshot.getExternalId())) {
                return true;
            }
            if (incrementAndCheckDuplicate(byInternalId, snapshot.getInternalId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalAlgoDuplicates(List<AlgoOrder> internalLiveAlgoOrders) {
        Map<String, Integer> byExternalId = new HashMap<>();
        Map<String, Integer> byInternalId = new HashMap<>();

        for (AlgoOrder order : safeAlgoOrders(internalLiveAlgoOrders)) {
            if (incrementAndCheckDuplicate(byExternalId, order.getExternalId())) {
                return true;
            }
            if (incrementAndCheckDuplicate(byInternalId, order.getInternalId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalAttachedDuplicates(List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        Map<String, Integer> byExternalAttachedId = new HashMap<>();
        Map<String, Integer> byExternalId = new HashMap<>();
        Map<String, Integer> byInternalId = new HashMap<>();

        for (AttachedAlgoOrder order : safeAttachedOrders(internalAttachedAlgoOrders)) {
            if (incrementAndCheckDuplicate(byExternalAttachedId, order.getExternalAttachedId())) {
                return true;
            }
            if (incrementAndCheckDuplicate(byExternalId, order.getExternalId())) {
                return true;
            }
            if (incrementAndCheckDuplicate(byInternalId, order.getInternalId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalAlgoOrdersFromAnotherInstrument(Instrument instrument,
                                                                List<AlgoOrder> internalLiveAlgoOrders) {
        for (AlgoOrder algoOrder : safeAlgoOrders(internalLiveAlgoOrders)) {
            if (Objects.isNull(algoOrder.getDealId())) {
                return true;
            }
            Long algoInstrumentId = instrumentService.findRequiredByDealId(algoOrder.getDealId()).getId();
            if (isFalse(Objects.equals(algoInstrumentId, instrument.getId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalAttachedOrdersFromAnotherInstrument(Instrument instrument,
                                                                   List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        for (AttachedAlgoOrder attachedAlgoOrder : safeAttachedOrders(internalAttachedAlgoOrders)) {
            if (Objects.isNull(attachedAlgoOrder.getOrderId())) {
                return true;
            }

            Order order = orderDataService.findRequiredById(attachedAlgoOrder.getOrderId());
            if (Objects.isNull(order.getDealId())) {
                return true;
            }

            Long attachedInstrumentId = instrumentService.findRequiredByDealId(order.getDealId()).getId();
            if (isFalse(Objects.equals(attachedInstrumentId, instrument.getId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnmatchedOrAmbiguousExternalAlgoOrders(List<AlgoOrderExternalSnapshot> externalSnapshots,
                                                               List<AlgoOrder> internalLiveAlgoOrders,
                                                               List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        for (AlgoOrderExternalSnapshot external : safeAlgoSnapshots(externalSnapshots)) {
            long matchesByExternalId = safeAlgoOrders(internalLiveAlgoOrders).stream()
                                                                              .filter(local -> Objects.equals(
                                                                                      local.getExternalId(),
                                                                                      external.getExternalId()))
                                                                              .count();
            matchesByExternalId += safeAttachedOrders(internalAttachedAlgoOrders).stream()
                                                                                  .filter(local -> Objects.equals(
                                                                                          local.getExternalId(),
                                                                                          external.getExternalId()))
                                                                                  .count();
            if (matchesByExternalId > 1) {
                return true;
            }
            if (matchesByExternalId == 1) {
                continue;
            }

            long matchesByInternalId = safeAlgoOrders(internalLiveAlgoOrders).stream()
                                                                              .filter(local -> Objects.equals(
                                                                                      local.getInternalId(),
                                                                                      external.getInternalId()))
                                                                              .count();
            matchesByInternalId += safeAttachedOrders(internalAttachedAlgoOrders).stream()
                                                                                  .filter(local -> Objects.equals(
                                                                                          local.getInternalId(),
                                                                                          external.getInternalId()))
                                                                                  .count();
            if (matchesByInternalId > 1) {
                return true;
            }
            if (matchesByInternalId == 1) {
                continue;
            }

            long matchesByExternalAttachedId = safeAttachedOrders(internalAttachedAlgoOrders).stream()
                                                                                               .filter(local -> Objects.equals(
                                                                                                       local.getExternalAttachedId(),
                                                                                                       external.getExternalId()))
                                                                                               .count();
            if (matchesByExternalAttachedId != 1) {
                return true;
            }
        }
        return false;
    }

    private String resolveRefreshPendingOrdersViolationCode(Instrument instrument,
                                                            List<OrderExternalSnapshot> externalPendingOrders,
                                                            List<Order> internalLiveOrders) {
        if (hasExternalDuplicates(externalPendingOrders)) {
            return REFRESH_PENDING_ORDERS_EXTERNAL_DUPLICATES;
        }

        if (hasInternalDuplicates(internalLiveOrders)) {
            return REFRESH_PENDING_ORDERS_INTERNAL_DUPLICATES;
        }

        if (hasInternalOrdersFromAnotherInstrument(instrument, internalLiveOrders)) {
            return REFRESH_PENDING_ORDERS_INVALID_INSTRUMENT_SCOPE;
        }

        if (hasUnmatchedOrAmbiguousExternalOrders(externalPendingOrders, internalLiveOrders)) {
            return REFRESH_PENDING_ORDERS_UNMATCHED_EXTERNAL;
        }

        return null;
    }

    private boolean hasExternalDuplicates(List<OrderExternalSnapshot> externalPendingOrders) {
        Map<String, Integer> byExternalId = new HashMap<>();
        Map<String, Integer> byInternalId = new HashMap<>();

        for (OrderExternalSnapshot snapshot : safeOrderSnapshots(externalPendingOrders)) {
            if (incrementAndCheckDuplicate(byExternalId, snapshot.getExternalId())) {
                return true;
            }
            if (incrementAndCheckDuplicate(byInternalId, snapshot.getInternalId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalDuplicates(List<Order> internalLiveOrders) {
        Map<String, Integer> byExternalId = new HashMap<>();
        Map<String, Integer> byInternalId = new HashMap<>();

        for (Order order : safeOrders(internalLiveOrders)) {
            if (incrementAndCheckDuplicate(byExternalId, order.getExternalId())) {
                return true;
            }
            if (incrementAndCheckDuplicate(byInternalId, order.getInternalId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnmatchedOrAmbiguousExternalOrders(List<OrderExternalSnapshot> externalPendingOrders,
                                                           List<Order> internalLiveOrders) {
        for (OrderExternalSnapshot externalOrder : safeOrderSnapshots(externalPendingOrders)) {
            long matchesByExternalId = safeOrders(internalLiveOrders).stream()
                                                                     .filter(local -> Objects.equals(
                                                                             local.getExternalId(),
                                                                             externalOrder.getExternalId()))
                                                                     .count();
            if (matchesByExternalId > 1) {
                return true;
            }
            if (matchesByExternalId == 1) {
                continue;
            }

            long matchesByInternalId = safeOrders(internalLiveOrders).stream()
                                                                     .filter(local -> Objects.equals(
                                                                             local.getInternalId(),
                                                                             externalOrder.getInternalId()))
                                                                     .count();
            if (matchesByInternalId != 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalOrdersFromAnotherInstrument(Instrument instrument, List<Order> internalLiveOrders) {
        for (Order order : safeOrders(internalLiveOrders)) {
            if (Objects.isNull(order.getDealId())) {
                return true;
            }
            Long orderInstrumentId = instrumentService.findRequiredByDealId(order.getDealId()).getId();
            if (isFalse(Objects.equals(orderInstrumentId, instrument.getId()))) {
                return true;
            }
        }
        return false;
    }

    private List<OrderExternalSnapshot> safeOrderSnapshots(List<OrderExternalSnapshot> snapshots) {
        if (Objects.isNull(snapshots)) {
            return List.of();
        }
        return snapshots;
    }

    private List<PositionExternalSnapshot> safePositionSnapshots(List<PositionExternalSnapshot> snapshots) {
        if (Objects.isNull(snapshots)) {
            return List.of();
        }
        return snapshots;
    }

    private List<Position> safePositions(List<Position> positions) {
        if (Objects.isNull(positions)) {
            return List.of();
        }
        return positions;
    }

    private List<Order> safeOrders(List<Order> orders) {
        if (Objects.isNull(orders)) {
            return List.of();
        }
        return orders;
    }

    private List<AlgoOrderExternalSnapshot> safeAlgoSnapshots(List<AlgoOrderExternalSnapshot> snapshots) {
        if (Objects.isNull(snapshots)) {
            return List.of();
        }
        return snapshots;
    }

    private List<AlgoOrder> safeAlgoOrders(List<AlgoOrder> orders) {
        if (Objects.isNull(orders)) {
            return List.of();
        }
        return orders;
    }

    private List<AttachedAlgoOrder> safeAttachedOrders(List<AttachedAlgoOrder> orders) {
        if (Objects.isNull(orders)) {
            return List.of();
        }
        return orders;
    }

    private boolean incrementAndCheckDuplicate(Map<String, Integer> counters, String key) {
        if (Objects.isNull(key) || key.isBlank()) {
            return false;
        }
        int nextCount = counters.getOrDefault(key, 0) + 1;
        counters.put(key, nextCount);
        return nextCount > 1;
    }

    private boolean hasOverheadPositions(List<PositionExternalSnapshot> externalSnapshots,
                                         List<Position> domainPositions) {
        return hasExternalOverheadPositions(externalSnapshots) || hasDomainOverheadPositions(domainPositions);
    }

    private boolean hasExternalPositionsFromAnotherInstrument(Instrument instrument,
                                                              List<PositionExternalSnapshot> externalSnapshots) {
        for (PositionExternalSnapshot snapshot : safePositionSnapshots(externalSnapshots)) {
            if (Objects.isNull(snapshot.getInstrumentExternalId())) {
                return true;
            }
            if (isFalse(Objects.equals(snapshot.getInstrumentExternalId(), instrument.getExternalId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalPositionsFromAnotherInstrument(Instrument instrument,
                                                              List<Position> domainPositions) {
        for (Position position : safePositions(domainPositions)) {
            if (Objects.isNull(position.getDealId())) {
                return true;
            }

            Long positionInstrumentId = instrumentService.findRequiredByDealId(position.getDealId()).getId();
            if (isFalse(Objects.equals(positionInstrumentId, instrument.getId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPositionIdentityMismatch(List<PositionExternalSnapshot> externalSnapshots,
                                                List<Position> domainPositions) {
        if (safePositionSnapshots(externalSnapshots).size() != INTEGER_ONE) {
            return false;
        }
        if (safePositions(domainPositions).size() != INTEGER_ONE) {
            return false;
        }

        PositionExternalSnapshot externalSnapshot = safePositionSnapshots(externalSnapshots).getFirst();
        Position domainPosition = safePositions(domainPositions).getFirst();
        if (Objects.isNull(externalSnapshot) || Objects.isNull(domainPosition)) {
            return false;
        }
        if (Objects.isNull(externalSnapshot.getExternalId())) {
            return false;
        }
        if (Objects.isNull(domainPosition.getExternalId())) {
            return false;
        }

        return isFalse(Objects.equals(externalSnapshot.getExternalId(), domainPosition.getExternalId()));
    }

    private boolean hasExternalOverheadPositions(List<PositionExternalSnapshot> externalSnapshots) {
        return isNotEmpty(externalSnapshots) && externalSnapshots.size() > INTEGER_ONE;
    }

    private boolean hasDomainOverheadPositions(List<Position> domainPositions) {
        return isNotEmpty(domainPositions) && domainPositions.size() > INTEGER_ONE;
    }

    private void executeTradeRuleViolationFlow(Exchange exchange,
                                               Instrument instrument,
                                               Long dealId,
                                               String code,
                                               Severity severity) {
        Status instrumentStatusBefore = instrument.getStatus();
        instrumentService.holdInstrument(instrument);

        AnomalyReport report = anomalyService.create(exchange.getId(), instrument.getId(), severity, code);

        ClientService clientService = clientManager.getClientService(exchange.getName());
        StateSnapshot beforeSnapshot = stateSnapshotReader.readBeforeSnapshot(clientService, instrument);
        String internalBefore = jsonUtils.buildInternalSnapshot(beforeSnapshot, instrument, instrumentStatusBefore);
        String externalBefore = jsonUtils.buildExternalSnapshot(beforeSnapshot, instrument);

        anomalyService.markInProgress(report.getId(), internalBefore, externalBefore);

        try {
            KillSwitchResult killSwitchResult =
                    killSwitchService.executeKillSwitch(exchange, instrument, dealId, code, beforeSnapshot);
            anomalyService.markKillSwitchExecuted(report.getId(),
                                                  killSwitchResult.getInternalAfter(),
                                                  killSwitchResult.getExternalAfter());

            if (killSwitchResult.isSuccess()) {
                anomalyService.complete(report.getId(), killSwitchResult.getMessage());
                if (severity == Severity.NON_CRITICAL) {
                    instrumentService.activateInstrument(instrument);
                    return;
                }
            } else {
                anomalyService.markError(report.getId(), killSwitchResult.getMessage());
            }
            instrumentService.blockInstrument(instrument);
        } catch (Exception exception) {
            String errorMessage = getRootCauseMessage(exception);
            anomalyService.markError(report.getId(), errorMessage);
            instrumentService.blockInstrument(instrument);
        }
    }
}
