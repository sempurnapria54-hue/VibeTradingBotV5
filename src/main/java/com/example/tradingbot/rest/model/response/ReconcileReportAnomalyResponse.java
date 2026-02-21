package com.example.tradingbot.rest.model.response;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconcileReportAnomalyResponse {

    private Long id;
    private Long reportId;
    private String instId;
    private String type;
    private String severity;
    private String summary;
    private String detailsJson;
    private Instant createdAt;
}

