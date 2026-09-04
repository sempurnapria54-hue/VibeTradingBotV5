package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Источник значения операнда условия. Операнд самоописателен:
 * sourceType + ссылка/значение по источнику; у вычисляемых источников
 * значение приходит в рантайме (evaluator). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyConditionOperand).
 */
public enum StrategyConditionSourceType {

    /** Рыночная цена (конкретный источник цены — поле priceSource операнда). */
    PRICE,

    /** Значение индикатора (ссылка по indicatorKey). */
    INDICATOR,

    /** Фаза рынка инструмента. */
    MARKET_PHASE,

    /** Структура рынка (ссылка по structureKey). */
    MARKET_STRUCTURE,

    /** Состояние позиции. */
    POSITION,

    /** Состояние ордера. */
    ORDER,

    /** Состояние algo-order. */
    ALGO_ORDER,

    /** Баланс счёта. */
    BALANCE,

    /** Время. */
    TIME,

    /** Константа-литерал (valueType + value на операнде). */
    CONSTANT
}
