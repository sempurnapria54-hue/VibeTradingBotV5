package com.example.tradingbot.domain.model.okxproxy;

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
    private String markPrice;
    private String liquidationPrice;
    private String unrealizedProfit;
    private String leverage;
    private String marginMode;
    private String updateTime;
}
