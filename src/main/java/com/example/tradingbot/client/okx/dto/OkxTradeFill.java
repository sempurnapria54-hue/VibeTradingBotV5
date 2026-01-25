package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxTradeFill {

    @JsonProperty("instId")
    private String exchangeInstrumentName;

    @JsonProperty("instType")
    private String instrumentType;

    @JsonProperty("ordId")
    private String exchangeOrderId;

    @JsonProperty("clOrdId")
    private String clientOrderId;

    @JsonProperty("tradeId")
    private String exchangeTradeId;

    @JsonProperty("billId")
    private String billId;

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("fillPx")
    private String price;

    @JsonProperty("fillSz")
    private String size;

    @JsonProperty("side")
    private String side;

    @JsonProperty("posSide")
    private String positionSide;

    @JsonProperty("execType")
    private String executionType;

    @JsonProperty("feeCcy")
    private String feeCurrency;

    @JsonProperty("fee")
    private String fee;

    @JsonProperty("ts")
    private String sourceTradeTime;
}
