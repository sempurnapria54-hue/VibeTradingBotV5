package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalOrder {

    private final String instId;
    private final String ordId;
    private final String clOrdId;
    private final String state;
    private final String ordType;
    private final String px;
    private final String sz;
    private final String fillSz;
    private final String avgPx;
    private final String fee;
    private final String cTime;
    private final String uTime;
}
