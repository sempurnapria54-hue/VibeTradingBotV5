package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.instrument.Instrument;
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
public class ExternalAlgoOrderReader {

    public List<AlgoOrderExternalSnapshot> readExternalAlgoOrders(ClientService clientService,
                                                                  Instrument instrument,
                                                                  List<AlgoOrder> internalAlgoOrders) {
        Set<String> types = buildTypes(internalAlgoOrders);
        Map<String, AlgoOrderExternalSnapshot> deduplicated = new LinkedHashMap<>();

        for (String type : types) {
            AlgoOrder probe = new AlgoOrder();
            probe.setExternalType(type);

            List<AlgoOrderExternalSnapshot> externalSnapshots = clientService.getActiveAlgoOrders(instrument, probe);
            for (AlgoOrderExternalSnapshot externalSnapshot : emptyIfNull(externalSnapshots)) {
                if (externalSnapshot == null) {
                    continue;
                }
                String key = externalSnapshot.getExternalId();
                if (key == null) {
                    key = type + "::" + deduplicated.size();
                }
                deduplicated.put(key, externalSnapshot);
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

        Set<String> unknownExternalTypes = emptyIfNull(internalAlgoOrders).stream()
                                                                          .filter(Objects::nonNull)
                                                                          .map(AlgoOrder::getExternalType)
                                                                          .filter(externalType -> doNotContains(types,
                                                                                                                externalType))
                                                                          .collect(Collectors.toSet());

        types.addAll(unknownExternalTypes);
        return types;
    }
}
