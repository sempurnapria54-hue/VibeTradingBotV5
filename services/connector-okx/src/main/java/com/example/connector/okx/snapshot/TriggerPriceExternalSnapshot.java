package com.example.connector.okx.snapshot;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Снапшот триггерной цены ноги algo-order: биржевой тип и значение.
 * Раздел AlgoOrderExternalSnapshot. См.
 * docs/models/domain/core/AlgoOrder.md (§External snapshots).
 */
@Value
@Builder
public class TriggerPriceExternalSnapshot {

    /** Биржевой тип цены. */
    String externalType;

    /** Биржевое значение. */
    BigDecimal externalValue;
}
