package com.example.tradingcore.domain.command;

/**
 * Вид действия, чьё исполнение отслеживает строка.
 *
 * <p><b>В схему НЕ персистится — вид кодируется таблицей:</b> стратегийные
 * исполнения живут в {@code deal_strategy_action_states}, системные в
 * {@code deal_system_action_states}. Вместе с nullable-колонкой рода
 * исчезает и неоднозначность ключа
 * (docs/models/domain/other/DealActionState.md §Инварианты).
 */
public enum ActionKind {

    /** Исполнение узла стратегии. */
    STRATEGY,

    /** Исполнение системного действия: добыча, финализация, терминалы. */
    SYSTEM
}
