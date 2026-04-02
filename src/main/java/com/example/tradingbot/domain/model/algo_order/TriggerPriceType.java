package com.example.tradingbot.domain.model.algo_order;

public enum TriggerPriceType {
    /**
     * Последняя цена
     */
    LAST,
    /**
     * Индексная цена
     */
    INDEX,
    /**
     * Цена маркировки
     */
    MARK
}
