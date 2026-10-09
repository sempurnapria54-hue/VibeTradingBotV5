package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Оператор правила условия. Перечень несёт только исполняемые значения:
 * шесть сравнений и два пересечения; оператор возвращается в перечень той
 * же правкой, что заводит его исполнение. Какой оператор допустим у какого
 * типа правила — docs/rules/strategy-condition-contract.md §«Правило и
 * операнды»; почему перечень сужен — §«Грамматика объявляет только
 * исполняемое» там же. См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyConditionRule).
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

    /** Пересёк снизу вверх. */
    CROSSED_ABOVE,

    /** Пересёк сверху вниз. */
    CROSSED_BELOW
}
