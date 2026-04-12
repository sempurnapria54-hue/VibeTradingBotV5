package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnomalyService {

    private final AnomalyReportDataService anomalyReportDataService;

    @Transactional
    public AnomalyReport create(AnomalyReport report) {
        report.setStatus(AnomalyReport.Status.CREATED);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport markInProgress(Long reportId, String message) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.setStatus(AnomalyReport.Status.IN_PROGRESS);
        report.setMessage(message);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport markKillSwitchExecuted(Long reportId, String message, String internalAfter, String externalAfter) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.setStatus(AnomalyReport.Status.KILL_SWITCH_EXECUTED);
        report.setMessage(message);
        report.setInternalAfter(internalAfter);
        report.setExternalAfter(externalAfter);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport complete(Long reportId, String message, String internalAfter, String externalAfter) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.setStatus(AnomalyReport.Status.COMPLETED);
        report.setMessage(message);
        report.setInternalAfter(internalAfter);
        report.setExternalAfter(externalAfter);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport markError(Long reportId, String message, String internalAfter, String externalAfter) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.setStatus(AnomalyReport.Status.ERROR);
        report.setMessage(message);
        report.setInternalAfter(internalAfter);
        report.setExternalAfter(externalAfter);
        return anomalyReportDataService.save(report);
    }
}
