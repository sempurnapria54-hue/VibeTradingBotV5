package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceTicker {

    private String instrumentId;
    private String lastPrice;
    private String askPrice;
    private String bidPrice;
    private String timestamp;
}
