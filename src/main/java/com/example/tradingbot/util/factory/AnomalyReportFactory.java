package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Status;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class AnomalyReportFactory {

    public static AnomalyReport createAnomalyReport(Long exchangeId,
                                                    Long instrumentId,
                                                    Severity severity,
                                                    String code,
                                                    String internalBefore,
                                                    String externalBefore) {
        AnomalyReport anomalyReport = new AnomalyReport();
        anomalyReport.setInternalId(UUID.randomUUID()
                                        .toString());
        anomalyReport.setStatus(Status.CREATED);
        anomalyReport.setExchangeId(exchangeId);
        anomalyReport.setInstrumentId(instrumentId);
        anomalyReport.setSeverity(severity);
        anomalyReport.setCode(code);
        anomalyReport.setInternalBefore(internalBefore);
        anomalyReport.setExternalBefore(externalBefore);
        return anomalyReport;
    }

}
