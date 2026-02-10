package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Candle {

    private String timestamp;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;
    private String volumeCurrency;
    private String volumeCurrencyQuote;
    private String confirmed;
}
