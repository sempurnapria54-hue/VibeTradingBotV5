package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalPosition;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class InstrumentBucketBuilder {

    public List<InstrumentBucket> buildBuckets(ExchangeSnapshot snapshot) {
        LinkedHashSet<String> instIds = new LinkedHashSet<>();
        snapshot.getPositions().stream().map(ExternalPosition::getInstId).forEach(instIds::add);
        snapshot.getOrders().stream().map(ExternalOrder::getInstId).forEach(instIds::add);
        snapshot.getAlgoOrders().stream().map(ExternalAlgoOrder::getInstId).forEach(instIds::add);

        List<InstrumentBucket> buckets = new ArrayList<>();
        for (String instId : instIds) {
            if (instId == null || instId.isBlank()) {
                continue;
            }
            List<ExternalPosition> positions = snapshot.getPositions().stream().filter(it -> instId.equals(it.getInstId())).toList();
            List<ExternalOrder> orders = snapshot.getOrders().stream().filter(it -> instId.equals(it.getInstId())).toList();
            List<ExternalAlgoOrder> algoOrders = snapshot.getAlgoOrders().stream().filter(it -> instId.equals(it.getInstId())).toList();
            buckets.add(InstrumentBucket.builder()
                    .instrumentName(instId)
                    .positions(positions)
                    .orders(orders)
                    .algoOrders(algoOrders)
                    .build());
        }
        buckets.sort(Comparator.comparing(InstrumentBucket::getInstrumentName));
        return buckets;
    }
}
