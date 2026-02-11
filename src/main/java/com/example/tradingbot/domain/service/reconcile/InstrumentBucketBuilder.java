package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.DatabaseSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalPosition;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InstrumentBucketBuilder {

    public List<InstrumentBucket> buildBuckets(ExchangeSnapshot exBefore) {
        return buildBuckets(
            DatabaseSnapshot.builder()
                .exchangeName(exBefore.getExchangeName())
                .capturedAtUtcMillis(exBefore.getCapturedAtUtcMillis())
                .instruments(List.of())
                .build(),
            exBefore
        );
    }

    public List<InstrumentBucket> buildBuckets(DatabaseSnapshot dbBefore, ExchangeSnapshot exBefore) {
        LinkedHashSet<String> instIds = new LinkedHashSet<>();
        dbBefore.getInstruments().stream().map(instrument -> instrument.getInstId()).forEach(instIds::add);
        exBefore.getInstruments().stream().map(instrument -> instrument.getInstId()).forEach(instIds::add);

        List<ExternalPosition> positions = exBefore.getPositions();
        List<ExternalOrder> orders = exBefore.getOrders();
        List<ExternalAlgoOrder> algoOrders = exBefore.getAlgoOrders();

        List<InstrumentBucket> buckets = new ArrayList<>();
        for (String instId : instIds) {
            if (instId == null || instId.isBlank()) {
                continue;
            }
            buckets.add(InstrumentBucket.builder()
                .instrumentName(instId)
                .positions(positions.stream().filter(it -> instId.equals(it.getInstId())).toList())
                .orders(orders.stream().filter(it -> instId.equals(it.getInstId())).toList())
                .algoOrders(algoOrders.stream().filter(it -> instId.equals(it.getInstId())).toList())
                .build());
        }
        buckets.sort(Comparator.comparing(InstrumentBucket::getInstrumentName));
        return buckets;
    }
}
