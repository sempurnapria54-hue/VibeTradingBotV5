package com.example.tradingbot.domain.model.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangePriceTicker {

    private String instrumentId;
    private String lastPrice;
    private String markPrice;
    private String indexPrice;
    private String askPrice;
    private String bidPrice;
    private String timestamp;
}
