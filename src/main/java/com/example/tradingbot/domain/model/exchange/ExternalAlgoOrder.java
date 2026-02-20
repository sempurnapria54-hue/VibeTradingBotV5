package com.example.tradingbot.domain.model.exchange;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalAlgoOrder {

    private final String instId;
    private final String algoId;
    private final String algoClOrdId;
    private final String state;
    private final String algoType;
    private final String sz;
    private final String triggerPx;
    private final String ordPx;
    private final String tpTriggerPx;
    private final String tpOrdPx;
    private final String slTriggerPx;
    private final String slOrdPx;
    private final String callbackRatio;
    private final String callbackSpread;
    private final String cTime;
    private final String uTime;
}
