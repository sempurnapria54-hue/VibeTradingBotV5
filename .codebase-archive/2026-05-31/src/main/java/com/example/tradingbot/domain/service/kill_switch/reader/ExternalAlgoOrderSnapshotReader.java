package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.tradingbot.util.CollectionUtils.doNotContains;
import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Component
public class ExternalAlgoOrderSnapshotReader {

    private static final Set<String> HISTORY_STATES = Set.of("canceled", "effective", "order_failed");

    public List<AlgoOrderExternalSnapshot> readActiveAlgoOrders(ClientService clientService,
                                                                Instrument instrument,
                                                                List<AlgoOrder> internalAlgoOrders) {
        return readActiveByTypes(clientService, instrument, buildTypes(internalAlgoOrders));
    }

    public List<AlgoOrderExternalSnapshot> readRelatedInactiveAlgoOrders(ClientService clientService,
                                                                         Instrument instrument,
                                                                         List<AlgoOrder> internalAlgoOrders) {
        return readHistoryByTypesAndStates(clientService, instrument, buildTypes(internalAlgoOrders));
    }

    private List<AlgoOrderExternalSnapshot> readActiveByTypes(ClientService clientService,
                                                              Instrument instrument,
                                                              Set<String> types) {
        Map<String, AlgoOrderExternalSnapshot> deduplicated = new LinkedHashMap<>();

        for (String type : types) {
            AlgoOrder probe = new AlgoOrder();
            probe.setExternalType(type);

            List<AlgoOrderExternalSnapshot> snapshots = clientService.getActiveAlgoOrders(instrument, probe);
            for (AlgoOrderExternalSnapshot snapshot : emptyIfNull(snapshots)) {
                if (snapshot == null) {
                    continue;
                }
                deduplicated.put(resolveKey(snapshot, type, deduplicated.size()), snapshot);
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private List<AlgoOrderExternalSnapshot> readHistoryByTypesAndStates(ClientService clientService,
                                                                        Instrument instrument,
                                                                        Set<String> types) {
        Map<String, AlgoOrderExternalSnapshot> deduplicated = new LinkedHashMap<>();

        for (String type : types) {
            for (String state : HISTORY_STATES) {
                AlgoOrder probe = new AlgoOrder();
                probe.setExternalType(type);
                probe.setExternalStatus(state);

                List<AlgoOrderExternalSnapshot> snapshots = clientService.getAlgoOrdersHistory(instrument, probe);
                for (AlgoOrderExternalSnapshot snapshot : emptyIfNull(snapshots)) {
                    if (snapshot == null) {
                        continue;
                    }
                    if (isLive(snapshot)) {
                        continue;
                    }
                    deduplicated.put(resolveKey(snapshot, type, deduplicated.size()), snapshot);
                }
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private Set<String> buildTypes(List<AlgoOrder> internalAlgoOrders) {
        Set<String> types = new LinkedHashSet<>();
        types.add("conditional");
        types.add("oco");
        types.add("trigger");
        types.add("move_order_stop");

        Set<String> customTypes = emptyIfNull(internalAlgoOrders).stream()
                                                                 .filter(Objects::nonNull)
                                                                 .map(AlgoOrder::getExternalType)
                                                                 .filter(externalType -> doNotContains(types,
                                                                                                       externalType))
                                                                 .collect(Collectors.toSet());
        types.addAll(customTypes);
        return types;
    }

    private String resolveKey(AlgoOrderExternalSnapshot snapshot, String type, int index) {
        if (snapshot.getExternalId() != null) {
            return snapshot.getExternalId();
        }
        return type + "::" + index;
    }

    private boolean isLive(AlgoOrderExternalSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.getExternalStatus() == null) {
            return false;
        }
        return "live".equalsIgnoreCase(snapshot.getExternalStatus())
                || "pause".equalsIgnoreCase(snapshot.getExternalStatus());
    }
}
