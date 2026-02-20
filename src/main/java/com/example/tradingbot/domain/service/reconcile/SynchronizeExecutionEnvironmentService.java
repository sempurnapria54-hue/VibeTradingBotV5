package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.config.ReconcileProperties;
import com.example.tradingbot.domain.service.reconcile.model.AnomalyDecision;
import com.example.tradingbot.domain.service.reconcile.model.CancelFlowResult;
import com.example.tradingbot.domain.service.reconcile.model.DatabaseSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.DbInstrumentState;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeInstrumentSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.domain.model.entity.PositionEntity;
import com.example.tradingbot.domain.model.entity.SynchronizeExecutionEnvironmentReportEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynchronizeExecutionEnvironmentService {

    private static final String STATUS_HOLD = "HOLD";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SYNC = "SYNC";

    private final ReconcileProperties reconcileProperties;
    private final OkxExchangeSnapshotProvider snapshotProvider;
    private final DatabaseSnapshotBuilder databaseSnapshotBuilder;
    private final InstrumentBucketBuilder bucketBuilder;
    private final SynchronizeExecutionEnvironmentReportService reportService;
    private final CountsOnlySyncEngine countsOnlySyncEngine;
    private final AnomalyEngine anomalyEngine;
    private final CancelExchangeFlow cancelExchangeFlow;
    private final ExchangeToDbTransferService exchangeToDbTransferService;
    private final ExchangeToDbExtendedTransferService exchangeToDbExtendedTransferService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    public void runDryRun() {
        if (BooleanUtils.isFalse(reconcileProperties.isEnabled())) {
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

    @Transactional
    public Long runSafe(Long exchangeId) {
        return run(exchangeId);
    }

    @Transactional
    public Long run(Long exchangeId) {
        if (BooleanUtils.isFalse(reconcileProperties.isEnabled())) {
            throw new IllegalStateException("Reconcile run skipped: reconcile.enabled=false");
        }

        ExchangeEntity exchange = exchangeDataService.findById(exchangeId)
            .orElseThrow(() -> new IllegalStateException("Reconcile run failed: exchange not found, id=" + exchangeId));

        List<InstrumentEntity> managedInstruments = instrumentDataService.findAllByExchangeId(exchange.getId());
        List<String> managedInstIds = managedInstruments.stream().map(InstrumentEntity::getExternalName).toList();

        DatabaseSnapshot databaseBefore = databaseSnapshotBuilder.captureDatabaseSnapshot(exchange, managedInstruments);
        ExchangeSnapshot exchangeBefore = snapshotProvider.captureExchangeSnapshot(managedInstIds);
        SynchronizeExecutionEnvironmentReportEntity report = reportService.createStartedReport(exchange, "SCHEDULED", databaseBefore, exchangeBefore);

        exchange.setStatus(STATUS_SYNC);
        exchangeDataService.save(exchange);

        List<InstrumentBucket> buckets = bucketBuilder.buildBuckets(databaseBefore, exchangeBefore);
        boolean hasAnomalies = false;
        String maxSeverity = "NONE";

        for (InstrumentBucket sourceBucket : buckets) {
            Optional<InstrumentEntity> instrumentOptional = instrumentDataService.findByExchangeIdAndName(exchange.getId(), sourceBucket.getInstrumentName());
            DbInstrumentState dbState = loadDbInstrumentState(exchange.getId(), instrumentOptional.orElse(null));
            InstrumentBucket bucket = InstrumentBucket.builder()
                .instrumentName(sourceBucket.getInstrumentName())
                .dbState(dbState)
                .positions(sourceBucket.getPositions())
                .orders(sourceBucket.getOrders())
                .algoOrders(sourceBucket.getAlgoOrders())
                .build();
            ExchangeInstrumentSnapshot currentExchangeState = toExchangeState(bucket);

            Optional<AnomalyDecision> decisionOptional = anomalyEngine.evaluate(bucket);
            if (decisionOptional.isPresent()) {
                AnomalyDecision decision = decisionOptional.get();
                reportService.appendAnomaly(
                    report.getId(),
                    bucket.getInstrumentName(),
                    decision.getType(),
                    decision.getSeverity(),
                    decision.getSummary(),
                    decision.getDetailsJson()
                );
                hasAnomalies = true;
                maxSeverity = resolveMaxSeverity(maxSeverity, decision.getSeverity());

                if (instrumentOptional.isPresent()) {
                    InstrumentEntity instrument = instrumentOptional.get();
                    if ("CRITICAL".equalsIgnoreCase(decision.getSeverity())) {
                        instrument.setStatus(STATUS_HOLD);
                        instrumentDataService.save(instrument);

                        CancelFlowResult cancelResult = cancelExchangeFlow.execute(bucket, decision);
                        if (cancelResult.isFlowExecuted()) {
                            currentExchangeState = cancelResult.getCurrentExchangeState();
                        }
                        continue;
                    }

                    CancelFlowResult cancelResult = cancelExchangeFlow.execute(bucket, decision);
                    if (cancelResult.isFlowExecuted()) {
                        currentExchangeState = cancelResult.getCurrentExchangeState();
                    }
                    instrument.setStatus("OPEN");
                    instrumentDataService.save(instrument);
                }
            }

            if (instrumentOptional.isPresent()) {
                InstrumentEntity instrument = instrumentOptional.get();
                InstrumentBucket syncBucket = InstrumentBucket.builder()
                    .instrumentName(bucket.getInstrumentName())
                    .dbState(dbState)
                    .positions(currentExchangeState.getPositions())
                    .orders(currentExchangeState.getOrders())
                    .algoOrders(currentExchangeState.getAlgoOrders())
                    .build();
                countsOnlySyncEngine.syncPresence(syncBucket, currentExchangeState);
                exchangeToDbTransferService.transfer(exchange.getId(), instrument.getId(), syncBucket);
                exchangeToDbExtendedTransferService.transfer(exchange.getId(), syncBucket, currentExchangeState, exchangeBefore);

                instrument.setStatus(STATUS_ACTIVE);
                instrumentDataService.save(instrument);
            }
        }

        ExchangeSnapshot exchangeAfter = snapshotProvider.captureExchangeSnapshot(managedInstIds);
        DatabaseSnapshot databaseAfter = databaseSnapshotBuilder.captureDatabaseSnapshot(exchange, managedInstruments);
        reportService.finalizeReport(report.getId(), databaseAfter, exchangeAfter, maxSeverity, hasAnomalies);

        exchange.setStatus(STATUS_ACTIVE);
        exchangeDataService.save(exchange);

        return report.getId();
    }

    @Transactional
    public Long runSafe() {
        return run();
    }

    @Transactional
    public Long run() {
        ExchangeEntity exchange = exchangeDataService.findByName("OKX")
            .orElseThrow(() -> new IllegalStateException("Reconcile run failed: exchange not found, name=OKX"));
        return run(exchange.getId());
    }

    private ExchangeInstrumentSnapshot toExchangeState(InstrumentBucket bucket) {
        return ExchangeInstrumentSnapshot.builder()
            .instId(bucket.getInstrumentName())
            .positionsCount(bucket.getPositionsCount())
            .ordersCount(bucket.getOrdersCount())
            .algoOrdersCount(bucket.getAlgoOrdersCount())
            .positions(bucket.getPositions())
            .orders(bucket.getOrders())
            .algoOrders(bucket.getAlgoOrders())
            .build();
    }

    private DbInstrumentState loadDbInstrumentState(Long exchangeId, InstrumentEntity instrument) {
        if (Objects.isNull(instrument)) {
            return DbInstrumentState.builder()
                .instrument(null)
                .activePositions(List.of())
                .activeOrders(List.of())
                .activeAlgoOrders(List.of())
                .build();
        }

        List<PositionEntity> positions = positionDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrument.getId()).stream()
            .filter(entity -> BooleanUtils.isFalse("CLOSED".equalsIgnoreCase(entity.getStatus())))
            .toList();
        List<OrderEntity> orders = orderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrument.getId()).stream()
            .filter(entity -> BooleanUtils.isFalse("CLOSED".equalsIgnoreCase(entity.getStatus())))
            .toList();
        List<AlgoOrderEntity> algoOrders = algoOrderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrument.getId()).stream()
            .filter(entity -> BooleanUtils.isFalse("CLOSED".equalsIgnoreCase(entity.getStatus())))
            .toList();

        return DbInstrumentState.builder()
            .instrument(instrument)
            .activePositions(positions)
            .activeOrders(orders)
            .activeAlgoOrders(algoOrders)
            .build();
    }

    private String resolveMaxSeverity(String currentMaxSeverity, String severity) {
        if ("CRITICAL".equalsIgnoreCase(currentMaxSeverity) || "CRITICAL".equalsIgnoreCase(severity)) {
            return "CRITICAL";
        }
        if ("NON_CRITICAL".equalsIgnoreCase(currentMaxSeverity) || "NON_CRITICAL".equalsIgnoreCase(severity)) {
            return "NON_CRITICAL";
        }
        return "NONE";
    }
}
