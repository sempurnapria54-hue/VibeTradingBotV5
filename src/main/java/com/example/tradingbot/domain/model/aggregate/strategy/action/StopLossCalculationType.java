package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Способ расчёта дистанции stop-loss. См.
 * docs/models/domain/aggregate/Strategy.md (§StopLossSettings).
 */
public enum StopLossCalculationType {

    /** Процент от цены входа. */
    ENTRY_PRICE_PERCENT,

    /** Процент от ATR (150 = 1.5 ATR); требует ссылку на ATR-настройку. */
    ATR_PERCENT,

    /**
     * Буфер за структурным уровнем (свинг/диапазон/поддержка/
     * сопротивление) — анти-stampede: стоп не ровно на очевидном уровне.
     */
    MARKET_STRUCTURE_BUFFER_PERCENT
}
