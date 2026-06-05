package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Геометрическая сторона смещения цены от базы (выше/ниже). Не путать
 * с {@link StrategyTradeDirection} (торговое направление). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyPricePlacement).
 */
public enum StrategyPriceOffsetSide {

    /** Смещение выше базы. */
    ABOVE,

    /** Смещение ниже базы. */
    BELOW
}
