package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.config.ReconcileProperties;
import com.example.tradingbot.domain.service.reconcile.model.AnomalyDecision;
import com.example.tradingbot.domain.service.reconcile.model.DbInstrumentState;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import com.example.tradingbot.persistence.service.SynchronizeExecutionEnvironmentReportDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.BooleanUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynchronizeExecutionEnvironmentService {

    private static final String STATUS_HOLD = "HOLD";

    private final ReconcileProperties reconcileProperties;
    private final OkxExchangeSnapshotProvider snapshotProvider;
    private final InstrumentBucketBuilder bucketBuilder;
    private final CountsOnlySyncEngine countsOnlySyncEngine;
    private final AnomalyEngine anomalyEngine;
    private final CancelExchangeFlow cancelExchangeFlow;
    private final ExchangeToDbTransferService exchangeToDbTransferService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;
    private final SynchronizeExecutionEnvironmentReportDataService reportDataService;
    private final ObjectMapper objectMapper;

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
    public void run() {
        if (BooleanUtils.isFalse(reconcileProperties.isEnabled())) {
            log.info("Reconcile run skipped: reconcile.enabled=false");
            return;
        }

        ExchangeSnapshot snapshot = snapshotProvider.captureSnapshot();
        List<InstrumentBucket> buckets = bucketBuilder.buildBuckets(snapshot);
        Optional<ExchangeEntity> exchangeOptional = exchangeDataService.findByName(snapshot.getExchangeName());
        if (exchangeOptional.isEmpty()) {
            log.warn("Reconcile run skipped: exchange not found in DB, exchangeName={}", snapshot.getExchangeName());
            return;
        }
        ExchangeEntity exchange = exchangeOptional.get();
        SynchronizeExecutionEnvironmentReportEntity report = reportDataService.createStarted(
            exchange,
            "SCHEDULED",
            serializeSnapshot(List.of()),
            serializeSnapshot(snapshot),
            Instant.now()
        );
        boolean hasAnomalies = false;
        String maxSeverity = "NONE";

        for (InstrumentBucket bucket : buckets) {
            Optional<InstrumentEntity> instrumentOptional = instrumentDataService.findByExchangeIdAndName(exchange.getId(), bucket.getInstrumentName());
            DbInstrumentState dbState = loadDbInstrumentState(exchange.getId(), instrumentOptional.orElse(null));

            if (instrumentOptional.isPresent()) {
                countsOnlySyncEngine.syncInstrumentBucket(exchange.getId(), instrumentOptional.get().getId(), snapshot, bucket);
                dbState = loadDbInstrumentState(exchange.getId(), instrumentOptional.get());
            }

            Optional<AnomalyDecision> decisionOptional = anomalyEngine.evaluate(snapshot, bucket, dbState);
            if (decisionOptional.isPresent()) {
                AnomalyDecision decision = decisionOptional.get();
                reportDataService.appendAnomaly(
                    report.getId(),
                    bucket.getInstrumentName(),
                    decision.getCategory(),
                    decision.getSeverity(),
                    decision.getSummary(),
                    decision.getDetailsJson(),
                    Instant.now()
                );
                hasAnomalies = true;
                maxSeverity = resolveMaxSeverity(maxSeverity, decision.getSeverity());

                if (decision.isShouldHold() && instrumentOptional.isPresent()) {
                    InstrumentEntity instrument = instrumentOptional.get();
                    instrument.setStatus(STATUS_HOLD);
                    instrumentDataService.save(instrument);
                }

                if (instrumentOptional.isPresent()
                    && reconcileProperties.getCancelFlow().isEnabled()
                    && decision.isShouldCancelFlow()) {
                    cancelExchangeFlow.execute(exchange.getId(), instrumentOptional.get().getId(), snapshot, bucket, decision);
                }
            }

            instrumentOptional.ifPresent(instrument -> exchangeToDbTransferService.transfer(exchange.getId(), instrument.getId(), bucket));
        }

        reportDataService.finalizeReport(
            report.getId(),
            serializeSnapshot(List.of()),
            serializeSnapshot(snapshotProvider.captureSnapshot()),
            Instant.now(),
            hasAnomalies,
            maxSeverity
        );
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

    private String serializeSnapshot(Object source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize reconcile snapshot", exception);
        }
    }
}
