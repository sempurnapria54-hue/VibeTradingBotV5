package com.example.tradingbot.rest.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Position {

    private String instrumentId;
    private String instrumentType;
    private String positionSide;
    private String positionSize;
    private String averagePrice;
    private String unrealizedProfit;
    private String leverage;
    private String marginMode;
}
