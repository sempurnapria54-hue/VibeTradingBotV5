package com.example.tradingbot.domain.model.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangeInstrument {

    /** Идентификатор инструмента на бирже (instId). */
    private String instrumentId;
    /** Тип инструмента: SPOT/MARGIN/SWAP/FUTURES/OPTION. */
    private String instrumentType;
    /** Базовая валюта инструмента. */
    private String baseCurrency;
    /** Валюта котировки инструмента. */
    private String quoteCurrency;
    /** Валюта расчётов по инструменту. */
    private String settleCurrency;
    /** Шаг изменения размера позиции/ордера. */
    private String lotSize;
    /** Минимально допустимый размер заявки. */
    private String minimumSize;
    /** Номинальная стоимость одного контракта. */
    private String contractValue;
    /** Мультипликатор контракта. */
    private String contractMultiplier;
    /** Минимальный шаг изменения цены. */
    private String tickSize;
}
