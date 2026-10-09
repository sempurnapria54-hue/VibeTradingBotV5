package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Источник значения операнда условия. Операнд самоописателен:
 * sourceType + ссылка/значение по источнику; у вычисляемых источников
 * значение приходит в рантайме (evaluator). Перечень несёт только
 * источники, которые оценка резолвит в значение, а набор полей операнда
 * задан его источником точно (docs/rules/strategy-condition-contract.md
 * §«Грамматика объявляет только исполняемое»). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyConditionOperand).
 */
public enum StrategyConditionSourceType {

    /** Последняя цена сделки инструмента в момент оценки; полей у операнда нет. */
    PRICE,

    /** Значение индикатора (ссылка по indicatorKey). */
    INDICATOR,

    /** Фаза рынка прохода; полей у операнда нет. */
    MARKET_PHASE,

    /** Структура рынка (ссылка по structureKey). */
    MARKET_STRUCTURE,

    /** Константа-литерал (valueType + value на операнде). */
    CONSTANT
}
