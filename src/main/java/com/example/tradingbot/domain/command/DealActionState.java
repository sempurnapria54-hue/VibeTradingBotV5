package com.example.tradingbot.domain.command;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted операционное состояние исполнения одного StrategyAction в
 * рамках Deal: на каком шаге исполнения находится action и какую
 * runtime-сущность он породил. Единственный держатель связи
 * StrategyAction ↔ Order/AlgoOrder/Position (сами они strategyActionId
 * не хранят). Несёт идемпотентность/recovery/retry command-layer'а;
 * retry-состояние наследует от Retryable. Инвариант
 * UNIQUE(dealId, strategyActionId) фиксируется в persistence
 * (docs/rules/idempotency-via-unique.md). См.
 * docs/models/domain/other/DealActionState.md,
 * docs/lifecycles/DealActionState.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class DealActionState extends Retryable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Сделка, в рамках которой выполняется action. */
    private Long dealId;

    /** Действие стратегии, чьё исполнение отслеживается. */
    private Long strategyActionId;

    /** Куда нацелено действие; null, пока сущность не создана (PLANNED). Персистится jsonb. */
    private RuntimeTarget target;

    /** Статус исполнения action. */
    private DealActionStateStatus status;
}
