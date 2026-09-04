package com.example.tradingbot.domain.command;

/**
 * Тип runtime-сущности, на которую нацелено исполнение действия
 * (DealActionState.targetEntityType). См.
 * docs/models/domain/other/DealActionState.md §Енумы.
 */
public enum TargetEntityType {

    /** Ordinary order. */
    ORDER,

    /** Standalone algo-order. */
    ALGO_ORDER,

    /** Позиция. */
    POSITION,

    /** Сама сделка — цель системных действий. */
    DEAL,

    /** Баланс. */
    BALANCE,

    /** Исполнение без runtime-цели (targetEntityId пуст). */
    NONE
}
