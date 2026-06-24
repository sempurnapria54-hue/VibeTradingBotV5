package com.example.tradingbot.domain.command;

/**
 * Дискриминатор финализационной команды сделки (1:1 с финализационными
 * значениями {@link ServiceCommandType}). Дом retry-state финализации —
 * {@link DealFinalizationState} (не DealActionState: финализация не
 * привязана к StrategyAction). См.
 * docs/models/domain/other/DealFinalizationState.md,
 * docs/decisions/deal-finalization-state-materialization.md.
 */
public enum DealFinalizationType {

    /** Консолидация результата входа (FINALIZE_DEAL_ENTRY). */
    FINALIZE_ENTRY,

    /** Консолидация фактов штатного выхода (FINALIZE_DEAL_EXIT). */
    FINALIZE_EXIT,

    /** Терминальное ребро штатного закрытия (MARK_DEAL_CLOSED). */
    MARK_CLOSED,

    /** Пометка ошибочного состояния сделки (MARK_DEAL_ERROR). */
    MARK_ERROR
}
