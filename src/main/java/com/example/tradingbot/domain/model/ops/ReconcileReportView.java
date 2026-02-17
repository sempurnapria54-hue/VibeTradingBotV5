package com.example.tradingbot.domain.model.ops;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReconcileReportView {

    private Long id;
    private Long exchangeId;
    private Instant startedAt;
    private Instant finishedAt;
    private String trigger;
    private boolean hasAnomalies;
    private String maxSeverity;
    private String databaseBeforeJson;
    private String exchangeBeforeJson;
    private String databaseAfterJson;
    private String exchangeAfterJson;
    private List<ReconcileReportAnomalyView> anomalies;
}
