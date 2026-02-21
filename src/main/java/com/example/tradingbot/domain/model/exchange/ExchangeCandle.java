package com.example.tradingbot.domain.model.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangeCandle {

    /** Временная метка свечи в миллисекундах UTC. */
    private String timestamp;
    /** Цена открытия свечи. */
    private String open;
    /** Максимальная цена свечи. */
    private String high;
    /** Минимальная цена свечи. */
    private String low;
    /** Цена закрытия свечи. */
    private String close;
    /** Объём торгов в базовой валюте. */
    private String volume;
    /** Объём торгов в валюте контракта/базы. */
    private String volumeCurrency;
    /** Объём торгов в валюте котировки. */
    private String volumeCurrencyQuote;
    /** Признак закрытой (подтверждённой) свечи. */
    private String confirmed;
}
