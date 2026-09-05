package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Источник рыночной цены. Не путать с TriggerPriceType (тип
 * trigger-цены биржи у algo-order). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyPricePlacement).
 */
public enum StrategyPriceSource {

    /** Последняя цена сделки. */
    LAST_PRICE,

    /** Марк-цена. */
    MARK_PRICE,

    /** Индексная цена. */
    INDEX_PRICE,

    /** Лучший бид. */
    BEST_BID_PRICE,

    /** Лучший аск. */
    BEST_ASK_PRICE,

    /** Середина спреда. */
    MID_PRICE
}
