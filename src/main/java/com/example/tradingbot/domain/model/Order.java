package com.example.tradingbot.domain.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Order {

    private String exchangeInstrumentName;
    private String instrumentType;
    private String exchangeOrderId;
    private String clientOrderId;
    private String tag;
    private String category;
    private String externalStatus;
    private String side;
    private String positionSide;
    private String tradeMode;
    private String marginCurrency;
    private String leverage;
    private String reduceOnly;
    private String quickMarginType;
    private String orderType;
    private String price;
    private String size;
    private String targetCurrencyMode;
    private String accumulatedFillSize;
    private String lastFillPrice;
    private String lastFillSize;
    private String lastFillTime;
    private String averageFillPrice;
    private String lastTradeId;
    private String fee;
    private String feeCurrency;
    private String rebate;
    private String rebateCurrency;
    private String pnl;
    private String attachAlgoClientOrderId;
    private String tpTriggerPrice;
    private String tpTriggerPriceType;
    private String tpOrderPrice;
    private String slTriggerPrice;
    private String slTriggerPriceType;
    private String slOrderPrice;
    private List<OrderAttachedAlgo> attachedAlgoOrders;
    private String linkedAlgoId;
    private String algoId;
    private String algoClientOrderId;
    private String tpLimit;
    private String priceType;
    private String priceUsd;
    private String priceVol;
    private String selfTradePreventionMode;
    private String selfTradePreventionId;
    private String source;
    private String cancelSource;
    private String cancelSourceReason;
    private String tradeQuoteCurrency;
    private String sourceCreatedAt;
    private String sourceUpdatedAt;
}
