package com.example.tradingbot.domain.service.ops;

import com.example.tradingbot.domain.model.ops.ReconcileReportAnomalyView;
import com.example.tradingbot.domain.model.ops.ReconcileReportView;
import com.example.tradingbot.domain.service.reconcile.SynchronizeExecutionEnvironmentService;
import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportAnomalyEntity;
import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportEntity;
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

    public List<ReconcileReportView> listReports(Long exchangeId, Integer limit) {
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
            .map(report -> toView(report, List.of()))
            .toList();
    }

    public ReconcileReportView getReport(Long id) {
        if (Objects.isNull(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }

        SynchronizeExecutionEnvironmentReportEntity report = reportDataService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconcile report not found"));

        List<ReconcileReportAnomalyView> anomalies = reportDataService.findAnomaliesByReportId(id)
            .stream()
            .map(this::toAnomalyView)
            .toList();

        return toView(report, anomalies);
    }

    private void validateRunRequest(String mode, Long exchangeId) {
        if (Objects.isNull(exchangeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exchangeId is required");
        }

        if (Objects.isNull(mode) || BooleanUtils.isTrue(mode.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode is required");
        }
    }

    private ReconcileReportView toView(SynchronizeExecutionEnvironmentReportEntity source, List<ReconcileReportAnomalyView> anomalies) {
        return new ReconcileReportView(
            source.getId(),
            source.getExchangeId(),
            source.getStartedAt(),
            source.getFinishedAt(),
            source.getTrigger(),
            source.isHasAnomalies(),
            source.getMaxSeverity(),
            source.getDatabaseBeforeJson(),
            source.getExchangeBeforeJson(),
            source.getDatabaseAfterJson(),
            source.getExchangeAfterJson(),
            anomalies
        );
    }

    private ReconcileReportAnomalyView toAnomalyView(SynchronizeExecutionEnvironmentReportAnomalyEntity source) {
        return new ReconcileReportAnomalyView(
            source.getId(),
            source.getInstId(),
            source.getType(),
            source.getSeverity(),
            source.getSummary(),
            source.getDetailsJson(),
            source.getCreatedAt()
        );
    }
}
