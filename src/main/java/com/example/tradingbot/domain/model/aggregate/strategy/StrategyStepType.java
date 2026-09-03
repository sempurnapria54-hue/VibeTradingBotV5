package com.example.tradingbot.domain.model.aggregate.strategy;

/**
 * Тип шага стратегии. Связь с DealTranche.entryStepType: ENTRY/GRID_ENTRY
 * → Deal.entryReason = STRATEGY + соответствующий тип входа НА ТРАНШЕ;
 * остальные типы входным шагом быть не могут.
 *
 * <p>Уровень объявления типом шага не задаётся: он читается носителем
 * строки (транш либо деталь). Тип — предмет проверки создания: узкая
 * агрегатная поверхность допускает только EXIT и FAIL_SAFE
 * (docs/rules/strategy-validation.md). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyStep).
 */
public enum StrategyStepType {

    /** Вход в позицию. */
    ENTRY,

    /** Основная standalone-защита после входа. */
    MAIN_PROTECTION,

    /** Корректировка защиты (перенос стопа, включение трейлинга). */
    PROTECTION_ADJUSTMENT,

    /** Частичный выход — только через reduce-only Order/AlgoOrder, не direct position. */
    PARTIAL_EXIT,

    /** Вход сеткой (grid). */
    GRID_ENTRY,

    /** Управление сеткой (снятие grid-входов и т. п.). */
    GRID_MANAGEMENT,

    /** Выход из позиции по условию стратегии. */
    EXIT,

    /** Страховочный шаг на нештатные состояния. */
    FAIL_SAFE
}
