package com.example.tradingbot.domain.service.ops;

import com.example.tradingbot.domain.service.reconcile.SynchronizeExecutionEnvironmentService;
import com.example.tradingbot.domain.model.entity.SynchronizeExecutionEnvironmentReportAnomalyEntity;
import com.example.tradingbot.domain.model.entity.SynchronizeExecutionEnvironmentReportEntity;
import com.example.tradingbot.persistence.service.SynchronizeExecutionEnvironmentReportDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReconcileOpsService {

    private static final int DEFAULT_LIMIT = 20;

    private final SynchronizeExecutionEnvironmentService synchronizeExecutionEnvironmentService;
    private final SynchronizeExecutionEnvironmentReportDataService reportDataService;

    public Long run(String mode, Long exchangeId) {
        validateRunRequest(mode, exchangeId);

        if (Objects.equals("SAFE", mode)) {
            return synchronizeExecutionEnvironmentService.runSafe(exchangeId);
        }

        if (Objects.equals("FULL", mode)) {
            return synchronizeExecutionEnvironmentService.run(exchangeId);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported mode: " + mode);
    }

    public List<SynchronizeExecutionEnvironmentReportEntity> listReports(Long exchangeId, Integer limit) {
        if (Objects.isNull(exchangeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exchangeId is required");
        }

        int resolvedLimit = DEFAULT_LIMIT;
        if (Objects.nonNull(limit)) {
            resolvedLimit = limit;
        }

        if (resolvedLimit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be greater than zero");
        }

        return reportDataService.findByExchangeId(exchangeId, resolvedLimit)
            .stream()
            .map(this::toReportWithoutAnomalies)
            .toList();
    }

    public SynchronizeExecutionEnvironmentReportEntity getReport(Long id) {
        if (Objects.isNull(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }

        SynchronizeExecutionEnvironmentReportEntity report = reportDataService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconcile report not found"));

        List<SynchronizeExecutionEnvironmentReportAnomalyEntity> anomalies = reportDataService.findAnomaliesByReportId(id)
            .stream()
            .map(this::toAnomalyEntity)
            .toList();

        return toReportEntity(report, anomalies);
    }

    private void validateRunRequest(String mode, Long exchangeId) {
        if (Objects.isNull(exchangeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exchangeId is required");
        }

        if (Objects.isNull(mode) || BooleanUtils.isTrue(mode.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode is required");
        }
    }

    private SynchronizeExecutionEnvironmentReportEntity toReportWithoutAnomalies(SynchronizeExecutionEnvironmentReportEntity source) {
        return toReportEntity(source, List.of());
    }

    private SynchronizeExecutionEnvironmentReportEntity toReportEntity(
        SynchronizeExecutionEnvironmentReportEntity source,
        List<SynchronizeExecutionEnvironmentReportAnomalyEntity> anomalies
    ) {
        SynchronizeExecutionEnvironmentReportEntity target = new SynchronizeExecutionEnvironmentReportEntity();
        target.setId(source.getId());
        target.setExchangeId(source.getExchangeId());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        target.setTrigger(source.getTrigger());
        target.setHasAnomalies(source.isHasAnomalies());
        target.setMaxSeverity(source.getMaxSeverity());
        target.setDatabaseBeforeJson(source.getDatabaseBeforeJson());
        target.setExchangeBeforeJson(source.getExchangeBeforeJson());
        target.setDatabaseAfterJson(source.getDatabaseAfterJson());
        target.setExchangeAfterJson(source.getExchangeAfterJson());
        target.setAnomalies(anomalies);
        return target;
    }

    private SynchronizeExecutionEnvironmentReportAnomalyEntity toAnomalyEntity(SynchronizeExecutionEnvironmentReportAnomalyEntity source) {
        SynchronizeExecutionEnvironmentReportAnomalyEntity target = new SynchronizeExecutionEnvironmentReportAnomalyEntity();
        target.setId(source.getId());
        target.setReportId(source.getReportId());
        target.setInstId(source.getInstId());
        target.setType(source.getType());
        target.setSeverity(source.getSeverity());
        target.setSummary(source.getSummary());
        target.setDetailsJson(source.getDetailsJson());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }
}
