package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Нормализованное торговое направление strategy-layer (runtime mapper
 * маппит в buy/sell, long/short биржи). Не путать с
 * {@link StrategyPriceOffsetSide} (геометрия смещения цены). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyTradeDirection).
 */
public enum StrategyTradeDirection {

    /** Длинная позиция. */
    LONG,

    /** Короткая позиция. */
    SHORT
}
