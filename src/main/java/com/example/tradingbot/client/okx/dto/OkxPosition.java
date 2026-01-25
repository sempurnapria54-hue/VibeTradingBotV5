package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxPosition {

    @JsonProperty("instId")
    private String exchangeInstrumentName;

    @JsonProperty("instType")
    private String instrumentType;

    @JsonProperty("mgnMode")
    private String marginMode;

    @JsonProperty("posSide")
    private String positionSide;

    @JsonProperty("ccy")
    private String marginCurrency;

    @JsonProperty("posId")
    private String exchangePosId;

    @JsonProperty("tradeId")
    private String lastTradeId;

    @JsonProperty("pos")
    private String positionContracts;

    @JsonProperty("avgPx")
    private String averagePrice;

    @JsonProperty("markPx")
    private String markPrice;

    @JsonProperty("last")
    private String lastPrice;

    @JsonProperty("bePx")
    private String breakEvenPrice;

    @JsonProperty("lever")
    private String leverage;

    @JsonProperty("liqPx")
    private String liquidationPrice;

    @JsonProperty("margin")
    private String positionMargin;

    @JsonProperty("notionalUsd")
    private String notionalUsd;

    @JsonProperty("mgnRatio")
    private String marginRatio;

    @JsonProperty("mmr")
    private String maintenanceMargin;

    @JsonProperty("adl")
    private String adl;

    @JsonProperty("upl")
    private String unrealizedPnl;

    @JsonProperty("uplRatio")
    private String unrealizedPnlRatio;

    @JsonProperty("realizedPnl")
    private String realizedPnl;

    @JsonProperty("fundingFee")
    private String fundingFee;

    @JsonProperty("fee")
    private String fee;

    @JsonProperty("closeOrderAlgo")
    private List<OkxPositionCloseAlgoOrder> closeAlgoOrders;

    @JsonProperty("cTime")
    private String sourceCreatedAt;

    @JsonProperty("uTime")
    private String sourceUpdatedAt;
}
