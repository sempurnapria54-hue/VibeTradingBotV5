package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxInstrumentDynamicSpec {

    @JsonProperty("tickSz")
    private String tickSize;

    @JsonProperty("lotSz")
    private String lotSize;

    @JsonProperty("minSz")
    private String minSize;

    @JsonProperty("maxLmtSz")
    private String maxLimitSize;

    @JsonProperty("maxMktSz")
    private String maxMarketSize;

    @JsonProperty("maxTwapSz")
    private String maxTwapSize;

    @JsonProperty("maxIcebergSz")
    private String maxIcebergSize;

    @JsonProperty("maxTriggerSz")
    private String maxTriggerSize;

    @JsonProperty("maxStopSz")
    private String maxStopSize;

    @JsonProperty("maxLmtAmt")
    private String maxLimitAmount;

    @JsonProperty("maxMktAmt")
    private String maxMarketAmount;

    @JsonProperty("state")
    private String exchangeState;

    @JsonProperty("lever")
    private String maxLeverage;

    @JsonProperty("ruleType")
    private String ruleType;

    @JsonProperty("openType")
    private String openType;

    @JsonProperty("category")
    private String category;

    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("uTime")
    private String sourceUpdatedAt;
}
