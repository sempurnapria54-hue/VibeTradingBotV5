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
                                String code,
                                String internalBefore,
                                String externalBefore) {
        AnomalyReport report = createAnomalyReport(exchangeId, instrumentId, severity, code, internalBefore,
                                                   externalBefore);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport markInProgress(Long reportId) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toInProgress();
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport markKillSwitchExecuted(Long reportId, String internalAfter, String externalAfter) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toKillSwitchExecuted(internalAfter, externalAfter);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport complete(Long reportId, String message, String internalAfter, String externalAfter) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toCompleted(internalAfter, externalAfter);
        return anomalyReportDataService.save(report);
    }

    @Transactional
    public AnomalyReport markError(Long reportId, String message, String internalAfter, String externalAfter) {
        AnomalyReport report = anomalyReportDataService.getRequiredById(reportId);
        report.toError(message, internalAfter, externalAfter);
        return anomalyReportDataService.save(report);
    }
}
