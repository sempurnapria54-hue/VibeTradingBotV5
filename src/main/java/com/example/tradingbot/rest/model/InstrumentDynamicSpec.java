package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstrumentDynamicSpec {

    private String tickSize;
    private String lotSize;
    private String minSize;
    private String maxLimitSize;
    private String maxMarketSize;
    private String maxTwapSize;
    private String maxIcebergSize;
    private String maxTriggerSize;
    private String maxStopSize;
    private String maxLimitAmount;
    private String maxMarketAmount;
    private String exchangeState;
    private String maxLeverage;
    private String ruleType;
    private String openType;
    private String category;
    private String groupId;
    private String sourceUpdatedAt;
}
