package com.example.tradingbot.rest.model.response.reconcile;

import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconcileReportResponse {

    private Long id;
    private String exchangeInternalId;
    private Instant startedAt;
    private Instant finishedAt;
    private String trigger;
    private boolean hasAnomalies;
    private String maxSeverity;
    private String databaseBeforeJson;
    private String exchangeBeforeJson;
    private String databaseAfterJson;
    private String exchangeAfterJson;
    private List<ReconcileReportAnomalyResponse> anomalies;
}
