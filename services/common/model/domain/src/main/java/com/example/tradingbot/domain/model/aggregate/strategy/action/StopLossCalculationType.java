package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Способ расчёта уровня stop-loss. Закрытый перечень и предикаты
 * согласованности — docs/spec/stop-distance.json.
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
    MARKET_STRUCTURE_BUFFER_PERCENT,

    /**
     * Безубыток: цена нулевого P&amp;L с учётом round-trip комиссии,
     * {@code якорь * (1 + f) / (1 - f)} для длинной позиции и
     * {@code якорь * (1 - f) / (1 + f)} для короткой. Якорь — фактическая
     * средняя цена входа живого эпизода. Для длинной позиции уровень лежит
     * выше якоря, для короткой — ниже; доля дистанции не объявляется.
     *
     * <p>Допустим только у действия, <b>переносящего</b> уже стоящий
     * уровень (защитный REPLACE с названной целью): первичной защитой
     * уровень на прибыльной стороне быть не может — worst-case выхода он
     * не задаёт. Реджект создания — STRATEGY_BREAKEVEN_NOT_A_TRANSFER
     * (docs/rules/strategy-validation.md).
     */
    BREAKEVEN
}
