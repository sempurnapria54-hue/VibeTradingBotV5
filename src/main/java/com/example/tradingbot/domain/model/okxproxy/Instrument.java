package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Instrument {

    private String instrumentId;
    private String instrumentType;
    private String baseCurrency;
    private String quoteCurrency;
    private String settleCurrency;
    private String lotSize;
    private String minimumSize;
    private String contractValue;
    private String contractMultiplier;
    private String tickSize;
}
