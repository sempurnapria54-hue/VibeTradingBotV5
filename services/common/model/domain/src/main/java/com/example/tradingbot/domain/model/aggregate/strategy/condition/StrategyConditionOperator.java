package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Оператор сравнивающего правила условия. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyConditionRule).
 */
public enum StrategyConditionOperator {

    /** Равно. */
    EQ,

    /** Не равно. */
    NE,

    /** Больше. */
    GT,

    /** Больше или равно. */
    GTE,

    /** Меньше. */
    LT,

    /** Меньше или равно. */
    LTE,

    /** Внутри диапазона (включительно). */
    BETWEEN,

    /** Вне диапазона. */
    NOT_BETWEEN,

    /** Пересёк снизу вверх. */
    CROSSED_ABOVE,

    /** Пересёк сверху вниз. */
    CROSSED_BELOW,

    /** Истинно. */
    IS_TRUE,

    /** Ложно. */
    IS_FALSE,

    /** Существует. */
    EXISTS,

    /** Не существует. */
    NOT_EXISTS
}
