package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxInstrument {

    @JsonProperty("instId")
    private String exchangeInstrumentName;

    @JsonProperty("instType")
    private String instrumentType;

    @JsonProperty("instFamily")
    private String instrumentFamily;

    @JsonProperty("uly")
    private String underlying;

    @JsonProperty("baseCcy")
    private String baseCurrency;

    @JsonProperty("quoteCcy")
    private String quoteCurrency;

    @JsonProperty("settleCcy")
    private String settleCurrency;

    @JsonProperty("ctType")
    private String contractType;

    @JsonProperty("ctVal")
    private String contractValue;

    @JsonProperty("ctValCcy")
    private String contractValueCurrency;

    @JsonProperty("ctMult")
    private String contractMultiplier;

    @JsonProperty("listTime")
    private String listedAt;

    @JsonProperty("expTime")
    private String expiresAt;

    @JsonProperty("alias")
    private String alias;

    @JsonUnwrapped
    private OkxInstrumentDynamicSpec dynamicSpec;
}
