package com.example.tradingbot.domain.service.candles.okx;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientCandle {

    long timestampMillis;
    BigDecimal open;
    BigDecimal high;
    BigDecimal low;
    BigDecimal close;
    BigDecimal volume;
    BigDecimal volumeCurrency;
    BigDecimal volumeCurrencyQuote;
}
