package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceTicker {

    private String exchangeInstrumentName;
    private String exchangeInstrumentType;
    private String lastPrice;
    private String lastSize;
    private String bestBidPrice;
    private String bestBidSize;
    private String bestAskPrice;
    private String bestAskSize;
    private String open24h;
    private String high24h;
    private String low24h;
    private String volume24h;
    private String volumeCurrency24h;
    private String startOfDayUtc0;
    private String startOfDayUtc8;
    private String sourceTimestamp;
}
