package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFill {

    private String billId;
    private String tradeId;
    private String orderId;
    private String instrumentId;
    private String side;
    private String fillSize;
    private String fillPrice;
    private String fillPnl;
    private String timestamp;
}
