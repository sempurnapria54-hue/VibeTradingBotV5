package com.example.tradingbot.domain.command;

/**
 * Тип runtime-сущности, на которую нацелено действие сделки
 * (RuntimeTarget.entityType). См.
 * docs/models/domain/other/DealActionState.md.
 */
public enum TargetEntityType {

    /** Ordinary order. */
    ORDER,

    /** Standalone algo-order. */
    ALGO_ORDER,

    /** Позиция. */
    POSITION,

    /** Сама сделка (lifecycle/finalization-action). */
    DEAL,

    /** Баланс (REFRESH_BALANCE). */
    BALANCE,

    /** Действие без runtime-target-сущности (entityId == null). */
    NONE
}
