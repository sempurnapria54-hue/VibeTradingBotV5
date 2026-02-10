package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandleDto {

    private String ts;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;
    private String volumeCurrency;
    private String volumeCurrencyQuote;
    private String confirm;
}
