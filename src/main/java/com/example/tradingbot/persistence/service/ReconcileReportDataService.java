package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.entity.ReconcileReportEntity;
import com.example.tradingbot.domain.model.entity.ReconcileAnomalyEntity;
import com.example.tradingbot.domain.model.exchange.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.DatabaseSnapshot;
import com.example.tradingbot.persistence.repository.SynchronizeExecutionEnvironmentReportAnomalyRepository;
import com.example.tradingbot.persistence.repository.SynchronizeExecutionEnvironmentReportRepository;
import com.example.tradingbot.util.JsonUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static com.example.tradingbot.util.Constant.ErrorCode.RECONCILE_REPORT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ReconcileReportDataService {

    private static final String DEFAULT_MAX_SEVERITY = "NONE";

    private final SynchronizeExecutionEnvironmentReportRepository reportRepository;
    private final SynchronizeExecutionEnvironmentReportAnomalyRepository anomalyRepository;
    private final JsonUtils jsonUtils;

    @Transactional
    public ReconcileReportEntity createStartedReport(Long exchangeId, String trigger, DatabaseSnapshot databaseBefore, ExchangeSnapshot exchangeBefore) {
        return createStarted(exchangeId, trigger, jsonUtils.toJson(databaseBefore), jsonUtils.toJson(exchangeBefore), Instant.now());
    }

    @Transactional
    public void appendAnomaly(Long reportId, String instId, String type, String severity, String summary, String detailsJson) {
        appendAnomaly(reportId, instId, type, severity, summary, detailsJson, Instant.now());
    }

    @Transactional
    public void finalizeReport(Long reportId, DatabaseSnapshot databaseAfter, ExchangeSnapshot exchangeAfter, String maxSeverity, boolean hasAnomalies) {
        finalizeReport(reportId, jsonUtils.toJson(databaseAfter), jsonUtils.toJson(exchangeAfter), Instant.now(), hasAnomalies, maxSeverity);
    }

    @Transactional
    public int deleteFinishedNoAnomaliesBefore(Instant threshold) {
        return Math.toIntExact(reportRepository.deleteAllByHasAnomaliesFalseAndFinishedAtBefore(threshold));
    }

    public List<ReconcileReportEntity> findByExchangeId(Long exchangeId, int limit) {
        return reportRepository.findAllByExchangeIdOrderByStartedAtDesc(exchangeId, PageRequest.of(0, limit)).getContent();
    }

    public List<ReconcileReportEntity> findByExchangeIdAndInstId(Long exchangeId, String instId, int limit) {
        return reportRepository
                .findDistinctByExchangeIdAndAnomaliesInstIdOrderByStartedAtDesc(exchangeId, instId, PageRequest.of(0, limit))
                .getContent();
    }

    public ReconcileReportEntity findRequiredById(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(RECONCILE_REPORT_NOT_FOUND));
    }

    public ReconcileReportEntity findRequiredByInternalId(String internalId) {
        return reportRepository.findByInternalId(internalId)
                .orElseThrow(() -> new EntityNotFoundException(RECONCILE_REPORT_NOT_FOUND));

    }

    public List<ReconcileAnomalyEntity> findAnomaliesByReportId(Long reportId) {
        return anomalyRepository.findAllByReportIdOrderByCreatedAtAsc(reportId);
    }

    private ReconcileReportEntity createStarted(Long exchangeId, String trigger, String databaseBeforeJson, String exchangeBeforeJson, Instant startedAt) {
        ReconcileReportEntity entity = new ReconcileReportEntity();
        entity.initOnCreate();
        entity.setExchangeId(exchangeId);
        entity.setTrigger(trigger);
        entity.setStartedAt(startedAt);
        entity.setHasAnomalies(false);
        entity.setMaxSeverity(DEFAULT_MAX_SEVERITY);
        entity.setDatabaseBeforeJson(databaseBeforeJson);
        entity.setExchangeBeforeJson(exchangeBeforeJson);
        return reportRepository.save(entity);
    }

    private void appendAnomaly(Long reportId, String instId, String type, String severity, String summary, String detailsJson, Instant createdAt) {
        ReconcileReportEntity report = findRequiredById(reportId);
        ReconcileAnomalyEntity anomaly = new ReconcileAnomalyEntity();
        anomaly.setReport(report);
        anomaly.setExternalInstrumentId(instId);
        anomaly.setType(type);
        anomaly.setSeverity(severity);
        anomaly.setSummary(summary);
        anomaly.setDetailsJson(detailsJson);
        anomaly.setCreatedAt(createdAt);
        anomalyRepository.save(anomaly);
    }

    private void finalizeReport(Long reportId, String databaseAfterJson, String exchangeAfterJson, Instant finishedAt, boolean hasAnomalies, String maxSeverity) {
        ReconcileReportEntity report = findRequiredById(reportId);
        report.setDatabaseAfterJson(databaseAfterJson);
        report.setExchangeAfterJson(exchangeAfterJson);
        report.setFinishedAt(finishedAt);
        report.setHasAnomalies(hasAnomalies);
        report.setMaxSeverity(maxSeverity);
    }
}
