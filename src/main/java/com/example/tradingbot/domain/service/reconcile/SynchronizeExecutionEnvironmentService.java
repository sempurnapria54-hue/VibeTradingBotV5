package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.config.ReconcileProperties;
import com.example.tradingbot.domain.service.reconcile.model.AnomalyDecision;
import com.example.tradingbot.domain.service.reconcile.model.DbInstrumentState;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.AnomalyReportEntity;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
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
    private final AnomalyReportDataService anomalyReportDataService;

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
                persistReport(exchange, instrumentOptional.orElse(null), decision);

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

    private void persistReport(ExchangeEntity exchange, InstrumentEntity instrument, AnomalyDecision decision) {
        AnomalyReportEntity entity = new AnomalyReportEntity();
        entity.setExchange(exchange);
        entity.setInstrument(instrument);
        entity.setSeverity(decision.getSeverity());
        entity.setCategory(decision.getCategory());
        entity.setSummary(decision.getSummary());
        entity.setDetailsJson(decision.getDetailsJson());
        anomalyReportDataService.save(entity);
    }
}
