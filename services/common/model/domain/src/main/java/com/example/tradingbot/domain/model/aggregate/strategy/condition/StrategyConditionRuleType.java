package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Тип правила условия стратегии. Какие поля правило несёт, тип задаёт
 * точно — названное обязательно, неназванное отвергает создание (дом —
 * docs/rules/strategy-condition-contract.md §«Правило и операнды»). Тип
 * входит в перечень только вместе с операндом, который оценка умеет
 * прочесть (там же, §«Тип без операнда не объявляется»).
 */
public enum StrategyConditionRuleType {

    /** По инструменту нет открытой позиции. */
    NO_OPEN_POSITION,

    /** По инструменту нет активной сделки. */
    NO_ACTIVE_DEAL,

    /** Входной ордер финализирован. */
    ENTRY_ORDER_FINALIZED,

    /** Позиция открыта. */
    POSITION_OPENED,

    /** У позиции существует attached stop-loss. */
    ATTACHED_STOP_LOSS_EXISTS,

    /** Существует основная standalone-защита. */
    MAIN_PROTECTION_EXISTS,

    /** Достигнут профит N% (поле percents). */
    PROFIT_PERCENTS_REACHED,

    /** Достигнут убыток N% (поле percents). */
    LOSS_PERCENTS_REACHED,

    /**
     * Пробой диапазона подтверждён в объявленном направлении — операнд
     * структуры и константа направления; буфер подтверждения — параметр
     * резолвера структуры, а не поле условия.
     */
    RANGE_BREAKOUT_CONFIRMED,

    /** Тренд сменился (темпоральное; в контексте фазы запрещено — нужна история). */
    TREND_CHANGED,

    /** Фаза рынка равна заданной (сравнение с CONSTANT ENUM). */
    MARKET_PHASE_IS,

    /** Структура рынка равна заданной (тест MarketStructure.Type, зеркало MARKET_PHASE_IS). */
    MARKET_STRUCTURE_IS,

    /** Сравнение с участием индикаторного операнда. */
    INDICATOR_COMPARE,

    /** Сравнение с участием ценового операнда. */
    PRICE_COMPARE,

    /** Кроссовер источников (операторы CROSSED_ABOVE / CROSSED_BELOW). */
    CROSSOVER,

    /** Объёмный фильтр пройден (объём — вспомогательный фильтр, см. IND-Q1). */
    VOLUME_FILTER_PASSED
}
