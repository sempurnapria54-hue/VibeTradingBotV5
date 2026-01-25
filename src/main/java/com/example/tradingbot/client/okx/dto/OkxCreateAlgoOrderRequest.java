package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCreateAlgoOrderRequest {

    @JsonProperty("instId")
    private String instId;

    @JsonProperty("tdMode")
    private String tdMode;

    @JsonProperty("side")
    private String side;

    @JsonProperty("posSide")
    private String posSide;

    @JsonProperty("ordType")
    private String ordType;

    @JsonProperty("sz")
    private String sz;

    @JsonProperty("closeFraction")
    private String closeFraction;

    @JsonProperty("reduceOnly")
    private Boolean reduceOnly;

    @JsonProperty("ccy")
    private String ccy;

    @JsonProperty("tgtCcy")
    private String tgtCcy;

    @JsonProperty("algoClOrdId")
    private String algoClOrdId;

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("tpTriggerPx")
    private String tpTriggerPx;

    @JsonProperty("tpTriggerPxType")
    private String tpTriggerPxType;

    @JsonProperty("tpOrdPx")
    private String tpOrdPx;

    @JsonProperty("slTriggerPx")
    private String slTriggerPx;

    @JsonProperty("slTriggerPxType")
    private String slTriggerPxType;

    @JsonProperty("slOrdPx")
    private String slOrdPx;

    @JsonProperty("triggerPx")
    private String triggerPx;

    @JsonProperty("triggerPxType")
    private String triggerPxType;

    @JsonProperty("orderPx")
    private String orderPx;

    @JsonProperty("callbackRatio")
    private String callbackRatio;

    @JsonProperty("callbackSpread")
    private String callbackSpread;

    @JsonProperty("activePx")
    private String activePx;
}
