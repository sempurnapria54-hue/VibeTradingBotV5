package com.example.tradingbot.domain.model.aggregate.strategy.condition;

/**
 * Тип правила условия стратегии. Per-ruleType контракт полей (какие
 * поля заполняются под каждый тип, включая валидность комбинаций
 * операндов) дозаполняется инкрементально при реализации каждого
 * ruleType (docs/decisions/strategy-condition-authoring-contract.md).
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

    /** Пробой диапазона подтверждён (буфер — поле percents). */
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
    VOLUME_FILTER_PASSED,

    /** Свеча таймфрейма закрыта (поле timeframe) — защита от look-ahead. */
    CANDLE_CLOSED
}
