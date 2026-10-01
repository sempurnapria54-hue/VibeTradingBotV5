package com.example.connector.okx.snapshot;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Снапшот trailing-механизма algo-order: цена активации и текущее
 * биржевое значение trailing. Раздел AlgoOrderExternalSnapshot. См.
 * docs/models/mapping/AlgoOrder.md (§«AlgoOrderExternalSnapshot →
 * AlgoOrder»).
 */
@Value
@Builder
public class TrailingExternalSnapshot {

    /** Цена активации trailing. */
    TriggerPriceExternalSnapshot activationPrice;

    /** Текущее биржевое значение trailing (OKX moveTriggerPx). */
    BigDecimal externalPrice;
}
