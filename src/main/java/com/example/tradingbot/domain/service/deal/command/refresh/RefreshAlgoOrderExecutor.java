package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.service.validator.TradeRuleValidator;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.AttachedAlgoOrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Service
@RequiredArgsConstructor
public class RefreshAlgoOrderExecutor {

    private static final Set<String> DEFAULT_ALGO_TYPES = Set.of("conditional", "oco", "trigger", "move_order_stop");
    private static final Set<String> LIVE_ALGO_STATUSES = Set.of(AlgoOrder.Status.PENDING.name(),
                                                                 AlgoOrder.Status.ACTIVE.name());
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
                LIVE_ALGO_STATUSES);
        List<AttachedAlgoOrder> internalAttachedAlgoOrders = attachedAlgoOrderDataService.findAllByInstrumentIdAndStatuses(
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
        Set<String> ordTypes = resolveOrdTypes(internalLiveAlgoOrders, internalAttachedAlgoOrders);

        Map<String, AlgoOrderExternalSnapshot> deduplicated = new LinkedHashMap<>();
        for (String ordType : ordTypes) {
            AlgoOrder probe = new AlgoOrder();
            probe.setExternalType(ordType);

            List<AlgoOrderExternalSnapshot> snapshots = clientService.getActiveAlgoOrders(instrument, probe);
            for (AlgoOrderExternalSnapshot snapshot : emptyIfNull(snapshots)) {
                deduplicated.putIfAbsent(externalSnapshotKey(snapshot), snapshot);
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private Set<String> resolveOrdTypes(List<AlgoOrder> internalLiveAlgoOrders,
                                        List<AttachedAlgoOrder> internalAttachedAlgoOrders) {
        Set<String> ordTypes = new LinkedHashSet<>(DEFAULT_ALGO_TYPES);

        for (AlgoOrder algoOrder : emptyIfNull(internalLiveAlgoOrders)) {
            if (algoOrder.hasExternalType()) {
                ordTypes.add(algoOrder.getExternalType());
            }
        }

        for (AttachedAlgoOrder attachedAlgoOrder : emptyIfNull(internalAttachedAlgoOrders)) {
            if (attachedAlgoOrder.hasExternalType()) {
                ordTypes.add(attachedAlgoOrder.getExternalType());
            }
        }

        return ordTypes;
    }

    private Set<String> syncMatchedLiveSnapshots(List<AlgoOrderExternalSnapshot> externalLiveSnapshots,
                                                 Map<String, MatchedLocalEntity> localIndex) {
        Set<String> matchedKeys = new LinkedHashSet<>();

        for (AlgoOrderExternalSnapshot external : emptyIfNull(externalLiveSnapshots)) {
            Optional<Map.Entry<String, MatchedLocalEntity>> matched = findMatchedLocal(localIndex, external);
            if (matched.isEmpty()) {
                continue;
            }

            matchedKeys.add(matched.get()
                                   .getKey());
            MatchedLocalEntity entity = matched.get()
                                               .getValue();
            entity.applyLive(external);
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
            AlgoOrderExternalSnapshot detail = tryReadDetail(exchange, entity.probe());
            if (Objects.nonNull(detail)) {
                entity.applyFinal(detail);
                continue;
            }

            AlgoOrderExternalSnapshot history = tryReadHistory(exchange, instrument, entity.probe());
            if (Objects.nonNull(history)) {
                entity.applyFinal(history);
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

    private AlgoOrderExternalSnapshot tryReadHistory(Exchange exchange, Instrument instrument, AlgoOrder probe) {
        ClientService clientService = clientManager.getClientService(exchange.getName());
        for (String status : HISTORY_FINAL_STATUSES) {
            AlgoOrder historyProbe = copyProbe(probe);
            historyProbe.setExternalStatus(status);

            List<AlgoOrderExternalSnapshot> historySnapshots = clientService.getAlgoOrdersHistory(instrument,
                                                                                                  historyProbe);
            Optional<AlgoOrderExternalSnapshot> matched = findMatch(historySnapshots, probe);
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        return null;
    }

    private Optional<AlgoOrderExternalSnapshot> findMatch(List<AlgoOrderExternalSnapshot> snapshots, AlgoOrder probe) {
        return emptyIfNull(snapshots).stream()
                                     .filter(snapshot ->
                                                     Objects.equals(snapshot.getExternalId(), probe.getExternalId())
                                                             || Objects.equals(snapshot.getInternalId(),
                                                                               probe.getInternalId())
                                     )
                                     .findFirst();
    }

    private Map<String, MatchedLocalEntity> buildLocalIndex(List<AlgoOrder> algoOrders,
                                                            List<AttachedAlgoOrder> attachedAlgoOrders) {
        Map<String, MatchedLocalEntity> result = new LinkedHashMap<>();

        for (AlgoOrder algoOrder : emptyIfNull(algoOrders)) {
            result.put(localEntityKey(algoOrder), MatchedLocalEntity.forAlgoOrder(algoOrder,
                                                                                  algoOrderDataService,
                                                                                  algoOrderSyncService));
        }

        for (AttachedAlgoOrder attachedAlgoOrder : emptyIfNull(attachedAlgoOrders)) {
            result.put(localEntityKey(attachedAlgoOrder),
                       MatchedLocalEntity.forAttachedAlgoOrder(attachedAlgoOrder,
                                                               attachedAlgoOrderDataService,
                                                               algoOrderSyncService));
        }

        return result;
    }

    private Optional<Map.Entry<String, MatchedLocalEntity>> findMatchedLocal(Map<String, MatchedLocalEntity> localIndex,
                                                                             AlgoOrderExternalSnapshot external) {
        return localIndex.entrySet()
                         .stream()
                         .filter(entry -> entry.getValue()
                                               .matches(external))
                         .findFirst();
    }

    private String externalSnapshotKey(AlgoOrderExternalSnapshot snapshot) {
        return "ext=" + safe(snapshot.getExternalId()) + "|int=" + safe(snapshot.getInternalId()) + "|type=" + safe(
                snapshot.getExternalType());
    }

    private String localEntityKey(AlgoOrder algoOrder) {
        return "algo|ext=" + safe(algoOrder.getExternalId()) + "|int=" + safe(algoOrder.getInternalId());
    }

    private String localEntityKey(AttachedAlgoOrder attachedAlgoOrder) {
        return "attached|extAtt=" + safe(attachedAlgoOrder.getExternalAttachedId())
                + "|ext=" + safe(attachedAlgoOrder.getExternalId())
                + "|int=" + safe(attachedAlgoOrder.getInternalId());
    }

    private AlgoOrder copyProbe(AlgoOrder source) {
        AlgoOrder copy = new AlgoOrder();
        copy.setInternalId(source.getInternalId());
        copy.setExternalId(source.getExternalId());
        copy.setExternalType(source.getExternalType());
        copy.setExternalStatus(source.getExternalStatus());
        return copy;
    }

    private String safe(String value) {
        if (Objects.isNull(value)) {
            return "";
        }

        return value;
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

        boolean matches(AlgoOrderExternalSnapshot external) {
            if (type == EntityType.ALGO) {
                if (Objects.equals(algoOrder.getExternalId(), external.getExternalId())
                        && Objects.nonNull(algoOrder.getExternalId())) {
                    return true;
                }
                return Objects.equals(algoOrder.getInternalId(), external.getInternalId())
                        && Objects.nonNull(algoOrder.getInternalId());
            }

            if (Objects.equals(attachedAlgoOrder.getExternalId(), external.getExternalId())
                    && Objects.nonNull(attachedAlgoOrder.getExternalId())) {
                return true;
            }
            if (Objects.equals(attachedAlgoOrder.getInternalId(), external.getInternalId())
                    && Objects.nonNull(attachedAlgoOrder.getInternalId())) {
                return true;
            }
            return Objects.equals(attachedAlgoOrder.getExternalAttachedId(), external.getExternalId())
                    && Objects.nonNull(attachedAlgoOrder.getExternalAttachedId());
        }

        void applyLive(AlgoOrderExternalSnapshot snapshot) {
            if (type == EntityType.ALGO) {
                algoOrderSyncService.applyLiveSnapshot(algoOrder, snapshot);
                algoOrderDataService.save(algoOrder);
                return;
            }
            algoOrderSyncService.applyLiveSnapshot(attachedAlgoOrder, snapshot);
            attachedAlgoOrderDataService.save(attachedAlgoOrder);
        }

        void applyFinal(AlgoOrderExternalSnapshot snapshot) {
            if (type == EntityType.ALGO) {
                algoOrderSyncService.applySnapshot(algoOrder, snapshot);
                algoOrderDataService.save(algoOrder);
                return;
            }
            algoOrderSyncService.applyFinalSnapshot(attachedAlgoOrder, snapshot);
            attachedAlgoOrderDataService.save(attachedAlgoOrder);
        }

        AlgoOrder probe() {
            AlgoOrder probe = new AlgoOrder();
            if (type == EntityType.ALGO) {
                probe.setExternalId(algoOrder.getExternalId());
                probe.setInternalId(algoOrder.getInternalId());
                probe.setExternalType(algoOrder.getExternalType());
                return probe;
            }

            probe.setExternalId(attachedAlgoOrder.getExternalId());
            probe.setInternalId(attachedAlgoOrder.getInternalId());
            probe.setExternalType(attachedAlgoOrder.getExternalType());
            return probe;
        }

        String debugId() {
            if (type == EntityType.ALGO) {
                return safe(algoOrder.getInternalId()) + "/" + safe(algoOrder.getExternalId());
            }
            return safe(attachedAlgoOrder.getInternalId()) + "/" + safe(attachedAlgoOrder.getExternalId()) + "/"
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
