package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class KillSwitchExternalAlgoOrderReader {

    public List<AlgoOrderExternalSnapshot> readExternalAlgoOrders(ClientService clientService,
                                                                  Instrument instrument,
                                                                  List<AlgoOrder> internalAlgoOrders) {
        Set<String> types = buildTypes(internalAlgoOrders);
        Map<String, AlgoOrderExternalSnapshot> deduplicated = new LinkedHashMap<>();

        for (String type : types) {
            AlgoOrder probe = new AlgoOrder();
            probe.setExternalType(type);

            List<AlgoOrderExternalSnapshot> externalSnapshots = nullSafeList(
                    clientService.getActiveAlgoOrders(instrument, probe));
            for (AlgoOrderExternalSnapshot externalSnapshot : externalSnapshots) {
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

        for (AlgoOrder internalAlgoOrder : nullSafeList(internalAlgoOrders)) {
            String externalType = internalAlgoOrder.getExternalType();
            if (externalType == null || externalType.isBlank()) {
                continue;
            }
            types.add(externalType);
        }

        return types;
    }

    private <T> List<T> nullSafeList(List<T> items) {
        if (items == null) {
            return List.of();
        }
        return items;
    }
}
