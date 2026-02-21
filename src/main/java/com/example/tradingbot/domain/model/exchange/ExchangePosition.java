package com.example.tradingbot.domain.model.exchange;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ExchangePosition {

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
