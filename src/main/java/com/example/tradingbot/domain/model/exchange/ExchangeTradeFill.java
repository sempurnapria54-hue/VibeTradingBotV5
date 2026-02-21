package com.example.tradingbot.domain.model.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangeTradeFill {

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
