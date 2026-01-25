package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFill {

    private String exchangeInstrumentName;
    private String instrumentType;
    private String exchangeOrderId;
    private String clientOrderId;
    private String exchangeTradeId;
    private String billId;
    private String tag;
    private String price;
    private String size;
    private String side;
    private String positionSide;
    private String executionType;
    private String feeCurrency;
    private String fee;
    private String sourceTradeTime;
}
