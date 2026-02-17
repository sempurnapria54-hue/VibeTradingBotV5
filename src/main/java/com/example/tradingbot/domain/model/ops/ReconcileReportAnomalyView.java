package com.example.tradingbot.domain.model.ops;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReconcileReportAnomalyView {

    private Long id;
    private String instId;
    private String type;
    private String severity;
    private String summary;
    private String detailsJson;
    private Instant createdAt;
}
