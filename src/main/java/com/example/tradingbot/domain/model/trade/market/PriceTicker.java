package com.example.tradingbot.domain.model.trade.market;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceTicker extends Auditable {

    /**
     * Идентификатор инструмента (instId).
     */
    private String externalInstrumentId;
    /**
     * Последняя цена сделки.
     */
    private String lastPrice;
    /**
     * Mark price инструмента.
     */
    private String markPrice;
    /**
     * Индексная цена инструмента.
     */
    private String indexPrice;
    /**
     * Лучшая цена продажи (ask).
     */
    private String askPrice;
    /**
     * Лучшая цена покупки (bid).
     */
    private String bidPrice;
    /**
     * Временная метка тикера в миллисекундах UTC.
     */
    private String timestamp;
}
