package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.factory.AnomalyReportFactory.createAnomalyReport;

@Service
@RequiredArgsConstructor
public class AnomalyService {

    private final AnomalyReportDataService anomalyReportDataService;

    @Transactional
    public AnomalyReport create(Long exchangeId,
                                Long instrumentId,
                                Severity severity,
                                String code) {
        AnomalyReport report = createAnomalyReport(exchangeId, instrumentId, severity, code);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public void markInProgress(Long reportId, String internalBefore, String externalBefore) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toInProgress(internalBefore, externalBefore);
        anomalyReportDataService.save(report);
    }

    @Transactional
    public void markKillSwitchExecuted(Long reportId,
                                       String internalAfter,
                                       String externalAfter) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toKillSwitchExecuted(internalAfter, externalAfter);
        anomalyReportDataService.save(report);
    }

    @Transactional
    public void complete(Long reportId, String message) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toCompleted(message);
        anomalyReportDataService.save(report);
    }

    @Transactional
    public void markError(Long reportId, String message) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toError(message);
        anomalyReportDataService.save(report);
    }
}
