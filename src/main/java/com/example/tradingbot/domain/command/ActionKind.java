package com.example.tradingbot.domain.command;

/**
 * Вид действия, чьё исполнение отслеживает строка. В схему НЕ
 * персистится — вид кодируется таблицей: стратегийные исполнения живут в
 * deal_strategy_action_states, системные в deal_system_action_states.
 * Вместе с nullable-колонкой рода исчезает и неоднозначность ключа. См.
 * docs/models/domain/other/DealActionState.md §Инварианты.
 */
public enum ActionKind {

    /** Исполнение узла стратегии (StrategyAction). */
    STRATEGY,

    /** Исполнение системного действия (добыча, финализация, терминалы). */
    SYSTEM
}
