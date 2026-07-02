package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Общий тип действия стратегии. Допустимые значения по подтипам:
 * ORDER/ALGO_ORDER — CREATE/REPLACE/CANCEL. Полного закрытия позиции как
 * действия нет: выход из позиции выражается условием-перехода
 * MANAGING → EXIT_PENDING (docs/decisions/fsm-execution-layering.md),
 * market-close ведёт ExitPendingHandler; частичное уменьшение — через
 * reduce-only Order/AlgoOrder (инвариант docs/rules/no-partial-close.md).
 * Enforcement семантики действий отложен до шагов 4/7 / activate
 * (docs/decisions/strategy-materialization-and-validation.md). См.
 * docs/models/domain/aggregate/Strategy.md (§Действия).
 */
public enum StrategyActionType {

    /** Создать runtime-сущность. */
    CREATE,

    /**
     * Ремоделировать runtime-сущность, созданную target-действием:
     * заместить новой сущностью + отменить старую (AMEND из домена
     * убран; docs/decisions/replace-not-amend.md).
     */
    REPLACE,

    /** Отменить runtime-сущность, созданную target-действием. */
    CANCEL
}
