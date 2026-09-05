package com.example.tradingbot.domain.model.aggregate.strategy.setting;

/**
 * Назначение настройки рыночных данных (индикатора / структуры рынка)
 * внутри стратегии: для чего считается результат. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyIndicatorSetting,
 * §StrategyMarketStructureSetting).
 */
public enum Destiny {

    /** Расчёт фазы рынка (уровень Strategy, до выбора detail). */
    MARKET_PHASE,

    /** Условие входа (ENTRY-условия шагов). */
    ENTRY_CONDITION,

    /** Расчёт цены действия (placement). */
    ACTION_PRICE,

    /** Защита позиции (stop-loss и его перенос). */
    PROTECTION,

    /** Условие выхода (EXIT-условия шагов). */
    EXIT_CONDITION
}
