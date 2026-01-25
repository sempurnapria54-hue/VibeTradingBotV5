package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxPositionCloseAlgoOrder {

    @JsonProperty("algoId")
    private String algoId;

    @JsonProperty("tpTriggerPx")
    private String tpTriggerPrice;

    @JsonProperty("tpTriggerPxType")
    private String tpTriggerPriceType;

    @JsonProperty("slTriggerPx")
    private String slTriggerPrice;

    @JsonProperty("slTriggerPxType")
    private String slTriggerPriceType;

    @JsonProperty("closeFraction")
    private String closeFraction;
}
