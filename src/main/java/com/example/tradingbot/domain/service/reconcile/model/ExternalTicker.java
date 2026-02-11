package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalTicker {

    private final String instId;
    private final String last;
    private final String markPx;
    private final String idxPx;
    private final String ts;
}

