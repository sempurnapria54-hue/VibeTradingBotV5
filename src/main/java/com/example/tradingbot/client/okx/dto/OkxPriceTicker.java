package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxPriceTicker {

    @JsonProperty("instId")
    private String exchangeInstrumentName;

    @JsonProperty("instType")
    private String exchangeInstrumentType;

    @JsonProperty("last")
    private String lastPrice;

    @JsonProperty("lastSz")
    private String lastSize;

    @JsonProperty("bidPx")
    private String bestBidPrice;

    @JsonProperty("bidSz")
    private String bestBidSize;

    @JsonProperty("askPx")
    private String bestAskPrice;

    @JsonProperty("askSz")
    private String bestAskSize;

    @JsonProperty("open24h")
    private String open24h;

    @JsonProperty("high24h")
    private String high24h;

    @JsonProperty("low24h")
    private String low24h;

    @JsonProperty("vol24h")
    private String volume24h;

    @JsonProperty("volCcy24h")
    private String volumeCurrency24h;

    @JsonProperty("sodUtc0")
    private String startOfDayUtc0;

    @JsonProperty("sodUtc8")
    private String startOfDayUtc8;

    @JsonProperty("ts")
    private String sourceTimestamp;
}
