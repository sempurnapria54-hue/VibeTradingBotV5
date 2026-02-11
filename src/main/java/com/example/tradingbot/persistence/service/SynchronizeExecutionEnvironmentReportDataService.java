package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportAnomalyEntity;
import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportEntity;
import com.example.tradingbot.persistence.repository.SynchronizeExecutionEnvironmentReportAnomalyRepository;
import com.example.tradingbot.persistence.repository.SynchronizeExecutionEnvironmentReportRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SynchronizeExecutionEnvironmentReportDataService {

    private static final String DEFAULT_MAX_SEVERITY = "NONE";

    private final SynchronizeExecutionEnvironmentReportRepository reportRepository;
    private final SynchronizeExecutionEnvironmentReportAnomalyRepository anomalyRepository;

    @Transactional
    public SynchronizeExecutionEnvironmentReportEntity createStarted(
        ExchangeEntity exchange,
        String trigger,
        String databaseBeforeJson,
        String exchangeBeforeJson,
        Instant startedAt
    ) {
        SynchronizeExecutionEnvironmentReportEntity entity = new SynchronizeExecutionEnvironmentReportEntity();
        entity.setExchange(exchange);
        entity.setTrigger(trigger);
        entity.setStartedAt(startedAt);
        entity.setHasAnomalies(false);
        entity.setMaxSeverity(DEFAULT_MAX_SEVERITY);
        entity.setDatabaseBeforeJson(databaseBeforeJson);
        entity.setExchangeBeforeJson(exchangeBeforeJson);
        return reportRepository.save(entity);
    }

    @Transactional
    public void appendAnomaly(
        Long reportId,
        String instId,
        String type,
        String severity,
        String summary,
        String detailsJson,
        Instant createdAt
    ) {
        SynchronizeExecutionEnvironmentReportEntity report = reportRepository.findById(reportId)
            .orElseThrow(() -> new EntityNotFoundException("SynchronizeExecutionEnvironmentReportEntity not found: id=" + reportId));

        SynchronizeExecutionEnvironmentReportAnomalyEntity anomaly = new SynchronizeExecutionEnvironmentReportAnomalyEntity();
        anomaly.setReport(report);
        anomaly.setInstId(instId);
        anomaly.setType(type);
        anomaly.setSeverity(severity);
        anomaly.setSummary(summary);
        anomaly.setDetailsJson(detailsJson);
        anomaly.setCreatedAt(createdAt);
        anomalyRepository.save(anomaly);
    }

    @Transactional
    public void finalizeReport(
        Long reportId,
        String databaseAfterJson,
        String exchangeAfterJson,
        Instant finishedAt,
        boolean hasAnomalies,
        String maxSeverity
    ) {
        SynchronizeExecutionEnvironmentReportEntity report = reportRepository.findById(reportId)
            .orElseThrow(() -> new EntityNotFoundException("SynchronizeExecutionEnvironmentReportEntity not found: id=" + reportId));
        report.setDatabaseAfterJson(databaseAfterJson);
        report.setExchangeAfterJson(exchangeAfterJson);
        report.setFinishedAt(finishedAt);
        report.setHasAnomalies(hasAnomalies);
        report.setMaxSeverity(maxSeverity);
    }

    @Transactional
    public int deleteFinishedNoAnomaliesBefore(Instant threshold) {
        return Math.toIntExact(reportRepository.deleteAllByHasAnomaliesFalseAndFinishedAtBefore(threshold));
    }
}
