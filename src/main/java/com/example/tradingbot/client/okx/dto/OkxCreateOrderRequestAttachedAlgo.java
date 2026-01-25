package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxCreateOrderRequestAttachedAlgo {

    @JsonProperty("attachAlgoClOrdId")
    private String attachAlgoClOrdId;

    @JsonProperty("tpTriggerPx")
    private String tpTriggerPx;

    @JsonProperty("tpTriggerRatio")
    private String tpTriggerRatio;

    @JsonProperty("tpOrdPx")
    private String tpOrdPx;

    @JsonProperty("tpOrdKind")
    private String tpOrdKind;

    @JsonProperty("tpTriggerPxType")
    private String tpTriggerPxType;

    @JsonProperty("slTriggerPx")
    private String slTriggerPx;

    @JsonProperty("slTriggerRatio")
    private String slTriggerRatio;

    @JsonProperty("slOrdPx")
    private String slOrdPx;

    @JsonProperty("slTriggerPxType")
    private String slTriggerPxType;

    @JsonProperty("sz")
    private String sz;

    @JsonProperty("amendPxOnTriggerType")
    private String amendPxOnTriggerType;
}
