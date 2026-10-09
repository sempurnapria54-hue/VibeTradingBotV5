package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Тип литерала операнда-константы (CONSTANT). У вычисляемых источников
 * (INDICATOR / PRICE / MARKET_STRUCTURE / MARKET_PHASE) тип
 * подразумевается источником и valueType не пишется. Литерал несёт
 * единое поле value (строковое представление), интерпретируемое по
 * этому типу; перечень несёт только типы, которые читает оценка
 * (docs/rules/strategy-condition-contract.md §«Грамматика объявляет
 * только исполняемое»). См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyConditionOperand).
 */
public enum ConstantValueType {

    /** Число (value — десятичная запись); скалярный тип. */
    NUMBER,

    /** Процент (value — десятичная запись процента); скалярный тип. */
    PERCENT,

    /** Значение enum (value — имя константы, например BULL_TREND). */
    ENUM
}
