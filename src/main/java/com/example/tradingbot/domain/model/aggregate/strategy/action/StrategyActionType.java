package com.example.tradingbot.domain.model.aggregate.strategy.action;

/**
 * Общий тип действия стратегии. Допустимые значения по подтипам:
 * ORDER/ALGO_ORDER — CREATE/AMEND/CANCEL; POSITION — только CLOSE_FULL
 * (инвариант no-partial-close, docs/rules/no-partial-close.md);
 * enforcement семантики действий отложен до шагов 4/7 / activate
 * (docs/decisions/strategy-materialization-and-validation.md). См.
 * docs/models/domain/aggregate/Strategy.md (§Действия).
 */
public enum StrategyActionType {

    /** Создать runtime-сущность. */
    CREATE,

    /** Изменить runtime-сущность, созданную target-действием. */
    AMEND,

    /** Отменить runtime-сущность, созданную target-действием. */
    CANCEL,

    /** Полное закрытие позиции (только StrategyPositionAction). */
    CLOSE_FULL
}
