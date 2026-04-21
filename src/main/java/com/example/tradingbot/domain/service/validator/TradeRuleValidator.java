package com.example.tradingbot.domain.service.validator;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.instrument.Instrument.Status;
import com.example.tradingbot.domain.model.kill_switch.KillSwitchResult;
import com.example.tradingbot.domain.model.kill_switch.StateSnapshot;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.service.anomaly.AnomalyService;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.domain.service.kill_switch.KillSwitchService;
import com.example.tradingbot.domain.service.kill_switch.reader.StateSnapshotReader;
import com.example.tradingbot.exception.TradeRuleViolationException;
import com.example.tradingbot.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ONE;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

@Service
@RequiredArgsConstructor
public class TradeRuleValidator {

    private static final String OVERHEAD_POSITIONS_COUNT = "OVERHEAD_POSITIONS_COUNT";
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

    public void validatePositions(Exchange exchange,
                                  Instrument instrument,
                                  Long dealId,
                                  List<PositionExternalSnapshot> externalSnapshots,
                                  List<Position> domainPositions) {
        if (hasOverheadPositions(externalSnapshots, domainPositions)) {
            executeTradeRuleViolationFlow(exchange,
                                          instrument,
                                          dealId,
                                          OVERHEAD_POSITIONS_COUNT,
                                          Severity.CRITICAL);
            throw new TradeRuleViolationException(
                    "Trade rule violation detected for positions: " + OVERHEAD_POSITIONS_COUNT);
        }
    }

    public void validateRefreshPendingOrders(Exchange exchange,
                                             Instrument instrument,
                                             Long dealId,
                                             List<OrderExternalSnapshot> externalPendingOrders,
                                             List<Order> internalLiveOrders) {
        String violationCode = resolveRefreshPendingOrdersViolationCode(instrument,
                                                                        externalPendingOrders,
                                                                        internalLiveOrders);
        if (violationCode == null) {
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
        if (violationCode == null) {
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
            if (algoOrder.getDealId() == null) {
                return true;
            }
            Long algoInstrumentId = instrumentService.findRequiredByDealId(algoOrder.getDealId()).getId();
            if (!Objects.equals(algoInstrumentId, instrument.getId())) {
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
            if (order.getDealId() == null) {
                return true;
            }
            Long orderInstrumentId = instrumentService.findRequiredByDealId(order.getDealId()).getId();
            if (!Objects.equals(orderInstrumentId, instrument.getId())) {
                return true;
            }
        }
        return false;
    }

    private List<OrderExternalSnapshot> safeOrderSnapshots(List<OrderExternalSnapshot> snapshots) {
        return snapshots == null ? List.of() : snapshots;
    }

    private List<Order> safeOrders(List<Order> orders) {
        return orders == null ? List.of() : orders;
    }

    private List<AlgoOrderExternalSnapshot> safeAlgoSnapshots(List<AlgoOrderExternalSnapshot> snapshots) {
        return snapshots == null ? List.of() : snapshots;
    }

    private List<AlgoOrder> safeAlgoOrders(List<AlgoOrder> orders) {
        return orders == null ? List.of() : orders;
    }

    private List<AttachedAlgoOrder> safeAttachedOrders(List<AttachedAlgoOrder> orders) {
        return orders == null ? List.of() : orders;
    }

    private boolean incrementAndCheckDuplicate(Map<String, Integer> counters, String key) {
        if (key == null || key.isBlank()) {
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
