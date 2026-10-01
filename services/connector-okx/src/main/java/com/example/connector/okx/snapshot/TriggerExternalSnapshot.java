package com.example.connector.okx.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * Снапшот trigger-механизма algo-order: ноги stop-loss и take-profit.
 * Раздел AlgoOrderExternalSnapshot. См.
 * docs/models/mapping/AlgoOrder.md (§«AlgoOrderExternalSnapshot →
 * AlgoOrder»).
 */
@Value
@Builder
public class TriggerExternalSnapshot {

    /** Нога stop-loss. */
    TriggerPriceExternalSnapshot stopLoss;

    /** Нога take-profit. */
    TriggerPriceExternalSnapshot takeProfit;
}
