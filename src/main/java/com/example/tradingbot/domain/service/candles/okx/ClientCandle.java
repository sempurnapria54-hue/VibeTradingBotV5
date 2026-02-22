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
//TODO: Для хранимых
//Request
//Controller         -> domain.Facade/Service -> proxy.service        -> exchange.client
//rest.model.request -> domain.model.entity   -> client.model.request -> exchange.model

//Response
//exchange.client -> proxy.service           -> domain.Facade/Service                                  -> Controller
//exchange.model  -> client.model.response   -> domain.model.exchange -> обновляем domain.model.entity -> rest.model.response


//TODO: Для пока что не хранимых
//Request
//Controller         -> domain.Facade/Service -> proxy.service        -> exchange.client
//rest.model.request -> rest.model.request    -> client.model.request -> exchange.model

//Response
//exchange.client -> proxy.service           -> domain.Facade/Service -> Controller
//exchange.model  -> client.model.response   -> domain.model.exchange -> rest.model.response