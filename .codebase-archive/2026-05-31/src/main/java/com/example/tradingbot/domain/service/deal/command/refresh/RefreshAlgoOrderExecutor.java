package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.service.validator.TradeRuleValidator;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.AttachedAlgoOrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class RefreshAlgoOrderExecutor {

    private static final Set<String> SUPPORTED_ALGO_TYPES = Set.of(
            "conditional",
            "oco",
            "trigger",
            "move_order_stop"
    );
    private static final Set<String> LIVE_ALGO_STATUSES = Set.of(
            AlgoOrder.Status.PENDING.name(),
            AlgoOrder.Status.ACTIVE.name()
    );
    private static final Set<String> LIVE_ATTACHED_STATUSES = AttachedAlgoOrder.activeLikeStatusNames();
    private static final Set<String> HISTORY_FINAL_STATUSES = Set.of("effective", "canceled", "order_failed");

    private final ClientManager clientManager;
    private final AlgoOrderDataService algoOrderDataService;
    private final AttachedAlgoOrderDataService attachedAlgoOrderDataService;
    private final TradeRuleValidator tradeRuleValidator;
    private final AlgoOrderSyncService algoOrderSyncService;

    @Transactional
    public void execute(Exchange exchange, Instrument instrument, Long dealId) {
        List<AlgoOrder> internalLiveAlgoOrders = algoOrderDataService.findAllByInstrumentIdAndStatuses(
                instrument.getId(),
                LIVE_ALGO_STATUSES
        );
        List<AttachedAlgoOrder> internalAttachedAlgoOrders =
                attachedAlgoOrderDataService.findAllByInstrumentIdAndStatuses(
                        instrument.getId(),
                        LIVE_ATTACHED_STATUSES
                );

        List<AlgoOrderExternalSnapshot> externalLiveSnapshots = readExternalLiveSnapshots(exchange,
                                                                                          instrument,
                                                                                          internalLiveAlgoOrders,
                                                                                          internalAttachedAlgoOrders);

        tradeRuleValidator.validateRefreshAlgoOrders(exchange,
                                                     instrument,
                                                     dealId,
                                                     externalLiveSnapshots,
                                                     internalLiveAlgoOrders,
                                                     internalAttachedAlgoOrders);

        Map<String, MatchedLocalEntity> localIndex = buildLocalIndex(internalLiveAlgoOrders,
                                                                     internalAttachedAlgoOrders);
        Set<String> matchedKeys = syncMatchedLiveSnapshots(externalLiveSnapshots, localIndex);
        finalizeMissingLiveEntities(exchange, instrument, localIndex, matchedKeys);
    }

    private List<AlgoOrderExternalSnapshot> readExternalLiveSnapshots(Exchange exchange,
                                                                      Instrument instrument,
                                                                      List<AlgoOrder> internalLiveAlgoOrders,
                                                                      List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        ClientService clientService = clientManager.getClientService(exchange.getName());
        Set<String> orderTypes = resolveOrdTypes(internalLiveAlgoOrders, internalAttachedAlgoOrders);
        Map<String, AlgoOrderExternalSnapshot> deduplicated = new LinkedHashMap<>();

        for (String orderType : orderTypes) {
            AlgoOrder probe = new AlgoOrder();
            probe.setExternalType(orderType);

            List<AlgoOrderExternalSnapshot> snapshots = clientService.getActiveAlgoOrders(instrument, probe);
            if (CollectionUtils.isEmpty(snapshots)) {
                continue;
            }

            for (AlgoOrderExternalSnapshot snapshot : snapshots) {
                deduplicated.putIfAbsent(externalSnapshotKey(snapshot), snapshot);
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private Set<String> resolveOrdTypes(List<AlgoOrder> internalLiveAlgoOrders,
                                        List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        Set<String> orderTypes = new LinkedHashSet<>(SUPPORTED_ALGO_TYPES);

        for (AlgoOrder algoOrder : safeAlgoOrders(internalLiveAlgoOrders)) {
            addSupportedOrderType(orderTypes, algoOrder.getExternalType());
        }

        for (AttachedAlgoOrder attachedAlgoOrder : safeAttachedOrders(internalAttachedAlgoOrders)) {
            addSupportedOrderType(orderTypes, attachedAlgoOrder.getExternalType());
        }

        return orderTypes;
    }

    private void addSupportedOrderType(Set<String> orderTypes, String candidateType) {
        if (isFalse(isNotBlank(candidateType))) {
            return;
        }
        if (isFalse(SUPPORTED_ALGO_TYPES.contains(candidateType))) {
            return;
        }

        orderTypes.add(candidateType);
    }

    private Set<String> syncMatchedLiveSnapshots(List<AlgoOrderExternalSnapshot> externalLiveSnapshots,
                                                 Map<String, MatchedLocalEntity> localIndex) {
        Set<String> matchedKeys = new LinkedHashSet<>();

        for (AlgoOrderExternalSnapshot externalSnapshot : safeSnapshots(externalLiveSnapshots)) {
            Optional<Map.Entry<String, MatchedLocalEntity>> matched = findMatchedLocal(localIndex, externalSnapshot);
            if (matched.isEmpty()) {
                continue;
            }

            matchedKeys.add(matched.get()
                                   .getKey());
            matched.get()
                   .getValue()
                   .applyLive(externalSnapshot);
        }

        return matchedKeys;
    }

    private void finalizeMissingLiveEntities(Exchange exchange,
                                             Instrument instrument,
                                             Map<String, MatchedLocalEntity> localIndex,
                                             Set<String> matchedKeys) {
        for (Map.Entry<String, MatchedLocalEntity> entry : localIndex.entrySet()) {
            if (matchedKeys.contains(entry.getKey())) {
                continue;
            }

            MatchedLocalEntity entity = entry.getValue();
            AlgoOrderExternalSnapshot detailSnapshot = tryReadDetail(exchange, entity.probe());
            if (Objects.nonNull(detailSnapshot)) {
                entity.applyFinal(detailSnapshot);
                continue;
            }

            AlgoOrderExternalSnapshot historySnapshot = tryReadHistory(exchange, instrument, entity);
            if (Objects.nonNull(historySnapshot)) {
                entity.applyFinal(historySnapshot);
                continue;
            }

            throw new IllegalStateException("Unresolved algo state for local entity: " + entity.debugId());
        }
    }

    private AlgoOrderExternalSnapshot tryReadDetail(Exchange exchange, AlgoOrder probe) {
        try {
            return clientManager.getClientService(exchange.getName())
                                .getAlgoOrder(probe);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private AlgoOrderExternalSnapshot tryReadHistory(Exchange exchange,
                                                     Instrument instrument,
                                                     MatchedLocalEntity entity) {
        ClientService clientService = clientManager.getClientService(exchange.getName());
        Set<String> historyOrderTypes = resolveHistoryOrderTypes(entity.probe());

        for (String historyOrderType : historyOrderTypes) {
            for (String historyStatus : HISTORY_FINAL_STATUSES) {
                AlgoOrder historyProbe = copyProbe(entity.probe(), historyOrderType, historyStatus);
                List<AlgoOrderExternalSnapshot> historySnapshots = clientService.getAlgoOrdersHistory(instrument,
                                                                                                      historyProbe);
                AlgoOrderExternalSnapshot matchedSnapshot = findMatchedSnapshot(historySnapshots, entity);
                if (Objects.nonNull(matchedSnapshot)) {
                    return matchedSnapshot;
                }
            }
        }

        return null;
    }

    private Set<String> resolveHistoryOrderTypes(AlgoOrder probe) {
        Set<String> historyOrderTypes = new LinkedHashSet<>();
        if (Objects.nonNull(probe) && SUPPORTED_ALGO_TYPES.contains(probe.getExternalType())) {
            historyOrderTypes.add(probe.getExternalType());
            return historyOrderTypes;
        }

        historyOrderTypes.addAll(SUPPORTED_ALGO_TYPES);
        return historyOrderTypes;
    }

    private AlgoOrderExternalSnapshot findMatchedSnapshot(List<AlgoOrderExternalSnapshot> snapshots,
                                                          MatchedLocalEntity entity) {
        for (AlgoOrderExternalSnapshot snapshot : safeSnapshots(snapshots)) {
            if (entity.matches(snapshot)) {
                return snapshot;
            }
        }

        return null;
    }

    private Map<String, MatchedLocalEntity> buildLocalIndex(List<AlgoOrder> algoOrders,
                                                            List<AttachedAlgoOrder> attachedAlgoOrders) {
        Map<String, MatchedLocalEntity> result = new LinkedHashMap<>();

        for (AlgoOrder algoOrder : safeAlgoOrders(algoOrders)) {
            result.put(localEntityKey(algoOrder), MatchedLocalEntity.forAlgoOrder(algoOrder,
                                                                                  algoOrderDataService,
                                                                                  algoOrderSyncService));
        }

        for (AttachedAlgoOrder attachedAlgoOrder : safeAttachedOrders(attachedAlgoOrders)) {
            result.put(localEntityKey(attachedAlgoOrder),
                       MatchedLocalEntity.forAttachedAlgoOrder(attachedAlgoOrder,
                                                               attachedAlgoOrderDataService,
                                                               algoOrderSyncService));
        }

        return result;
    }

    private Optional<Map.Entry<String, MatchedLocalEntity>> findMatchedLocal(Map<String, MatchedLocalEntity> localIndex,
                                                                             AlgoOrderExternalSnapshot externalSnapshot) {
        return localIndex.entrySet()
                         .stream()
                         .filter(entry -> entry.getValue()
                                               .matches(externalSnapshot))
                         .findFirst();
    }

    private String externalSnapshotKey(AlgoOrderExternalSnapshot snapshot) {
        return "ext=" + safe(snapshot.getExternalId())
                + "|int=" + safe(snapshot.getInternalId())
                + "|type=" + safe(snapshot.getExternalType());
    }

    private String localEntityKey(AlgoOrder algoOrder) {
        return "algo|ext=" + safe(algoOrder.getExternalId()) + "|int=" + safe(algoOrder.getInternalId());
    }

    private String localEntityKey(AttachedAlgoOrder attachedAlgoOrder) {
        return "attached|extAtt=" + safe(attachedAlgoOrder.getExternalAttachedId())
                + "|ext=" + safe(attachedAlgoOrder.getExternalId())
                + "|int=" + safe(attachedAlgoOrder.getInternalId());
    }

    private AlgoOrder copyProbe(AlgoOrder source, String externalType, String externalStatus) {
        AlgoOrder copy = new AlgoOrder();
        copy.setInternalId(source.getInternalId());
        copy.setExternalId(source.getExternalId());
        copy.setExternalType(externalType);
        copy.setExternalStatus(externalStatus);
        return copy;
    }

    private List<AlgoOrderExternalSnapshot> safeSnapshots(List<AlgoOrderExternalSnapshot> snapshots) {
        if (CollectionUtils.isEmpty(snapshots)) {
            return List.of();
        }
        return snapshots;
    }

    private List<AlgoOrder> safeAlgoOrders(List<AlgoOrder> orders) {
        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }
        return orders;
    }

    private List<AttachedAlgoOrder> safeAttachedOrders(List<AttachedAlgoOrder> orders) {
        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }
        return orders;
    }

    private String safe(String value) {
        if (Objects.isNull(value)) {
            return "";
        }
        return value;
    }

    private boolean isNotBlank(String value) {
        return Objects.nonNull(value) && isFalse(value.isBlank());
    }

    private record MatchedLocalEntity(EntityType type,
                                      AlgoOrder algoOrder,
                                      AttachedAlgoOrder attachedAlgoOrder,
                                      AlgoOrderDataService algoOrderDataService,
                                      AttachedAlgoOrderDataService attachedAlgoOrderDataService,
                                      AlgoOrderSyncService algoOrderSyncService) {

        static MatchedLocalEntity forAlgoOrder(AlgoOrder algoOrder,
                                               AlgoOrderDataService algoOrderDataService,
                                               AlgoOrderSyncService algoOrderSyncService) {
            return new MatchedLocalEntity(EntityType.ALGO,
                                          algoOrder,
                                          null,
                                          algoOrderDataService,
                                          null,
                                          algoOrderSyncService);
        }

        static MatchedLocalEntity forAttachedAlgoOrder(AttachedAlgoOrder attachedAlgoOrder,
                                                       AttachedAlgoOrderDataService attachedAlgoOrderDataService,
                                                       AlgoOrderSyncService algoOrderSyncService) {
            return new MatchedLocalEntity(EntityType.ATTACHED,
                                          null,
                                          attachedAlgoOrder,
                                          null,
                                          attachedAlgoOrderDataService,
                                          algoOrderSyncService);
        }

        boolean matches(AlgoOrderExternalSnapshot externalSnapshot) {
            if (Objects.equals(type, EntityType.ALGO)) {
                if (Objects.nonNull(algoOrder.getExternalId())
                        && Objects.equals(algoOrder.getExternalId(), externalSnapshot.getExternalId())) {
                    return true;
                }
                return Objects.nonNull(algoOrder.getInternalId())
                        && Objects.equals(algoOrder.getInternalId(), externalSnapshot.getInternalId());
            }

            if (Objects.nonNull(attachedAlgoOrder.getExternalId())
                    && Objects.equals(attachedAlgoOrder.getExternalId(), externalSnapshot.getExternalId())) {
                return true;
            }
            if (Objects.nonNull(attachedAlgoOrder.getInternalId())
                    && Objects.equals(attachedAlgoOrder.getInternalId(), externalSnapshot.getInternalId())) {
                return true;
            }
            return Objects.nonNull(attachedAlgoOrder.getExternalAttachedId())
                    && Objects.equals(attachedAlgoOrder.getExternalAttachedId(), externalSnapshot.getExternalId());
        }

        void applyLive(AlgoOrderExternalSnapshot snapshot) {
            if (Objects.equals(type, EntityType.ALGO)) {
                algoOrderSyncService.applyLiveSnapshot(algoOrder, snapshot);
                algoOrderDataService.save(algoOrder);
                return;
            }

            algoOrderSyncService.applyLiveSnapshot(attachedAlgoOrder, snapshot);
            attachedAlgoOrderDataService.save(attachedAlgoOrder);
        }

        void applyFinal(AlgoOrderExternalSnapshot snapshot) {
            if (Objects.equals(type, EntityType.ALGO)) {
                algoOrderSyncService.applySnapshot(algoOrder, snapshot);
                algoOrderDataService.save(algoOrder);
                return;
            }

            algoOrderSyncService.applyFinalSnapshot(attachedAlgoOrder, snapshot);
            attachedAlgoOrderDataService.save(attachedAlgoOrder);
        }

        AlgoOrder probe() {
            AlgoOrder probe = new AlgoOrder();
            if (Objects.equals(type, EntityType.ALGO)) {
                probe.setExternalId(algoOrder.getExternalId());
                probe.setInternalId(algoOrder.getInternalId());
                probe.setExternalType(algoOrder.getExternalType());
                return probe;
            }

            String externalProbeId = attachedAlgoOrder.getExternalId();
            if (Objects.isNull(externalProbeId)) {
                externalProbeId = attachedAlgoOrder.getExternalAttachedId();
            }

            probe.setExternalId(externalProbeId);
            probe.setInternalId(attachedAlgoOrder.getInternalId());
            probe.setExternalType(attachedAlgoOrder.getExternalType());
            return probe;
        }

        String debugId() {
            if (Objects.equals(type, EntityType.ALGO)) {
                return safe(algoOrder.getInternalId()) + "/" + safe(algoOrder.getExternalId());
            }

            return safe(attachedAlgoOrder.getInternalId())
                    + "/"
                    + safe(attachedAlgoOrder.getExternalId())
                    + "/"
                    + safe(attachedAlgoOrder.getExternalAttachedId());
        }

        private String safe(String value) {
            if (Objects.isNull(value)) {
                return "";
            }
            return value;
        }
    }

    private enum EntityType {
        ALGO,
        ATTACHED
    }
}
