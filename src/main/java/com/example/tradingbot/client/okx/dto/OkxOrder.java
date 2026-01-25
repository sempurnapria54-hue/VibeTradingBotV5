package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxOrder {

    @JsonProperty("instId")
    private String exchangeInstrumentName;

    @JsonProperty("instType")
    private String instrumentType;

    @JsonProperty("ordId")
    private String exchangeOrderId;

    @JsonProperty("clOrdId")
    private String clientOrderId;

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("category")
    private String category;

    @JsonProperty("state")
    private String externalStatus;

    @JsonProperty("side")
    private String side;

    @JsonProperty("posSide")
    private String positionSide;

    @JsonProperty("tdMode")
    private String tradeMode;

    @JsonProperty("ccy")
    private String marginCurrency;

    @JsonProperty("lever")
    private String leverage;

    @JsonProperty("reduceOnly")
    private String reduceOnly;

    @JsonProperty("quickMgnType")
    private String quickMarginType;

    @JsonProperty("ordType")
    private String orderType;

    @JsonProperty("px")
    private String price;

    @JsonProperty("sz")
    private String size;

    @JsonProperty("tgtCcy")
    private String targetCurrencyMode;

    @JsonProperty("accFillSz")
    private String accumulatedFillSize;

    @JsonProperty("fillPx")
    private String lastFillPrice;

    @JsonProperty("fillSz")
    private String lastFillSize;

    @JsonProperty("fillTime")
    private String lastFillTime;

    @JsonProperty("avgPx")
    private String averageFillPrice;

    @JsonProperty("tradeId")
    private String lastTradeId;

    @JsonProperty("fee")
    private String fee;

    @JsonProperty("feeCcy")
    private String feeCurrency;

    @JsonProperty("rebate")
    private String rebate;

    @JsonProperty("rebateCcy")
    private String rebateCurrency;

    @JsonProperty("pnl")
    private String pnl;

    @JsonProperty("attachAlgoClOrdId")
    private String attachAlgoClientOrderId;

    @JsonProperty("tpTriggerPx")
    private String tpTriggerPrice;

    @JsonProperty("tpTriggerPxType")
    private String tpTriggerPriceType;

    @JsonProperty("tpOrdPx")
    private String tpOrderPrice;

    @JsonProperty("slTriggerPx")
    private String slTriggerPrice;

    @JsonProperty("slTriggerPxType")
    private String slTriggerPriceType;

    @JsonProperty("slOrdPx")
    private String slOrderPrice;

    @JsonProperty("attachAlgoOrds")
    private List<OkxOrderAttachedAlgo> attachedAlgoOrders;

    @JsonProperty("linkedAlgoOrd")
    private OkxLinkedAlgoOrder linkedAlgoOrder;

    @JsonProperty("algoId")
    private String algoId;

    @JsonProperty("algoClOrdId")
    private String algoClientOrderId;

    @JsonProperty("isTpLimit")
    private String tpLimit;

    @JsonProperty("pxType")
    private String priceType;

    @JsonProperty("pxUsd")
    private String priceUsd;

    @JsonProperty("pxVol")
    private String priceVol;

    @JsonProperty("stpId")
    private String selfTradePreventionId;

    @JsonProperty("stpMode")
    private String selfTradePreventionMode;

    @JsonProperty("source")
    private String source;

    @JsonProperty("cancelSource")
    private String cancelSource;

    @JsonProperty("cancelSourceReason")
    private String cancelSourceReason;

    @JsonProperty("tradeQuoteCcy")
    private String tradeQuoteCurrency;

    @JsonProperty("cTime")
    private String sourceCreatedAt;

    @JsonProperty("uTime")
    private String sourceUpdatedAt;
}
