package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnomalyDecision {

    private final String type;
    private final String severity;
    private final boolean shouldHold;
    private final boolean shouldCancelFlow;
    private final String summary;
    private final String detailsJson;
}
