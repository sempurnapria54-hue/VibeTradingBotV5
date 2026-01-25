package com.example.tradingbot.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Instrument {

    private String exchangeInstrumentName;
    private String instrumentType;
    private String instrumentFamily;
    private String underlying;
    private String baseCurrency;
    private String quoteCurrency;
    private String settleCurrency;
    private String contractType;
    private String contractValue;
    private String contractValueCurrency;
    private String contractMultiplier;
    private String listedAt;
    private String expiresAt;
    private String alias;
    private InstrumentDynamicSpec dynamicSpec;
}
