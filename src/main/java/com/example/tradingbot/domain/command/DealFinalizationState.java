package com.example.tradingbot.domain.command;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted операционное состояние выполнения одной финализационной
 * команды (lifecycle/system action) в рамках Deal: FINALIZE_ENTRY /
 * FINALIZE_EXIT / MARK_CLOSED / MARK_ERROR. Несёт идемпотентность /
 * recovery / retry финализационного контура там, где DealActionState не
 * подходит (финализация не привязана к StrategyAction, многокомандна,
 * ретраится по-командно). Цель финализации — всегда сама Deal (dealId),
 * отдельного RuntimeTarget нет. Retry-состояние — от {@link Retryable}.
 * Инвариант UNIQUE(deal_id, finalization_type) в persistence. См.
 * docs/models/domain/other/DealFinalizationState.md,
 * docs/decisions/deal-finalization-state-materialization.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class DealFinalizationState extends Retryable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Сделка, чья финализация отслеживается (она же — цель). */
    private Long dealId;

    /** Какая финализационная команда отслеживается (дискриминатор). */
    private DealFinalizationType type;

    /** Статус исполнения финализации (см. lifecycle). */
    private DealFinalizationStateStatus status;
}
