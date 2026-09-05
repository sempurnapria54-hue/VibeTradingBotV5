package com.example.connector.okx.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * Снапшот условия срабатывания algo-order: trigger и/или trailing
 * (как пришло с биржи). Раздел AlgoOrderExternalSnapshot. См.
 * docs/models/domain/core/AlgoOrder.md (§External snapshots).
 */
@Value
@Builder
public class ConditionExternalSnapshot {

    /** Trigger-механизм (SL/TP/OCO/PARTIAL_*). */
    TriggerExternalSnapshot trigger;

    /** Trailing-механизм (TRAILING_*). */
    TrailingExternalSnapshot trailing;
}
