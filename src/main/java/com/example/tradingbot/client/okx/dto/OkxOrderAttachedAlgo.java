package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxOrderAttachedAlgo {

    @JsonProperty("attachAlgoId")
    private String attachAlgoId;

    @JsonProperty("attachAlgoClOrdId")
    private String attachAlgoClientOrderId;

    @JsonProperty("tpOrdKind")
    private String tpOrderKind;

    @JsonProperty("tpTriggerPx")
    private String tpTriggerPrice;

    @JsonProperty("tpTriggerRatio")
    private String tpTriggerRatio;

    @JsonProperty("tpTriggerPxType")
    private String tpTriggerPriceType;

    @JsonProperty("tpOrdPx")
    private String tpOrderPrice;

    @JsonProperty("slTriggerPx")
    private String slTriggerPrice;

    @JsonProperty("slTriggerRatio")
    private String slTriggerRatio;

    @JsonProperty("slTriggerPxType")
    private String slTriggerPriceType;

    @JsonProperty("slOrdPx")
    private String slOrderPrice;

    @JsonProperty("sz")
    private String size;

    @JsonProperty("amendPxOnTriggerType")
    private String amendPriceOnTriggerType;

    @JsonProperty("failCode")
    private String failCode;

    @JsonProperty("failReason")
    private String failReason;
}
