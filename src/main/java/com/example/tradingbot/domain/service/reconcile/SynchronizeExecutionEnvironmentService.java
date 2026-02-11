package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.config.ReconcileProperties;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynchronizeExecutionEnvironmentService {

    private final ReconcileProperties reconcileProperties;
    private final OkxExchangeSnapshotProvider snapshotProvider;
    private final InstrumentBucketBuilder bucketBuilder;

    public void runDryRun() {
        if (!reconcileProperties.isEnabled()) {
            log.info("Reconcile dry-run skipped: reconcile.enabled=false");
            return;
        }

        ExchangeSnapshot snapshot = snapshotProvider.captureSnapshot();
        List<InstrumentBucket> buckets = bucketBuilder.buildBuckets(snapshot);

        log.info("Reconcile dry-run started: exchangeName={}, capturedAt={}", snapshot.getExchangeName(), Instant.ofEpochMilli(snapshot.getCapturedAtUtcMillis()));
        log.info("Reconcile dry-run totals: Positions.count={}, Orders.count={}, AlgoOrders.count={}",
                snapshot.getPositions().size(),
                snapshot.getOrders().size(),
                snapshot.getAlgoOrders().size());

        for (InstrumentBucket bucket : buckets) {
            log.info("Reconcile dry-run bucket: instId={}, Positions.count={}, Orders.count={}, AlgoOrders.count={}",
                    bucket.getInstrumentName(),
                    bucket.getPositionsCount(),
                    bucket.getOrdersCount(),
                    bucket.getAlgoOrdersCount());
        }
    }
}
