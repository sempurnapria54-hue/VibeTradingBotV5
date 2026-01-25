package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxAmendOrderRequestAttachedAlgo {

    @JsonProperty("attachAlgoId")
    private String attachAlgoId;

    @JsonProperty("attachAlgoClOrdId")
    private String attachAlgoClOrdId;

    @JsonProperty("newTpTriggerPx")
    private String newTpTriggerPx;

    @JsonProperty("newTpTriggerRatio")
    private String newTpTriggerRatio;

    @JsonProperty("newTpOrdPx")
    private String newTpOrdPx;

    @JsonProperty("newTpOrdKind")
    private String newTpOrdKind;

    @JsonProperty("newSlTriggerPx")
    private String newSlTriggerPx;

    @JsonProperty("newSlTriggerRatio")
    private String newSlTriggerRatio;

    @JsonProperty("newSlOrdPx")
    private String newSlOrdPx;

    @JsonProperty("newTpTriggerPxType")
    private String newTpTriggerPxType;

    @JsonProperty("newSlTriggerPxType")
    private String newSlTriggerPxType;

    @JsonProperty("sz")
    private String sz;

    @JsonProperty("amendPxOnTriggerType")
    private String amendPxOnTriggerType;
}
