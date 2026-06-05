package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * База расчёта цены размещения (StrategyPricePlacement.baseType):
 * структурный уровень, цена входа или рыночная цена. Значения уровней
 * совпадают с MarketPriceLevel.Type
 * (docs/models/domain/other/MarketStructure.md). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyPricePlacement).
 */
public enum StrategyPriceBaseType {

    /** Нижняя граница диапазона. */
    RANGE_LOW,

    /** Верхняя граница диапазона. */
    RANGE_HIGH,

    /** Свинг-лоу. */
    SWING_LOW,

    /** Свинг-хай. */
    SWING_HIGH,

    /** Уровень поддержки. */
    SUPPORT,

    /** Уровень сопротивления. */
    RESISTANCE,

    /** Цена входа в позицию. */
    ENTRY_PRICE,

    /** Текущая рыночная цена (конкретный источник — priceSource). */
    MARKET_PRICE
}
