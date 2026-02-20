package com.example.tradingbot.domain.model.exchange;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalPosition {

    private final String instId;
    private final String side;
    private final String pos;
    private final String avgPx;
    private final String markPx;
    private final String liqPx;
    private final String lever;
    private final String mgnMode;
    private final String upl;
    private final String uTime;
}
