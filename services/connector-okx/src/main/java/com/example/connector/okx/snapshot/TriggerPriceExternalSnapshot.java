package com.example.connector.okx.snapshot;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Снапшот триггерной цены ноги algo-order: биржевой тип и значение.
 * Раздел AlgoOrderExternalSnapshot. См.
 * docs/models/mapping/AlgoOrder.md (§«AlgoOrderExternalSnapshot →
 * AlgoOrder»).
 */
@Value
@Builder
public class TriggerPriceExternalSnapshot {

    /**
     * Эхо ценовой базы — литералом источника (OKX last/index/mark); в
     * доменный перечень переводится на переходе снапшота в домен, пусто и вне
     * перечня — пусто (docs/models/mapping/AlgoOrder.md).
     */
    String externalType;

    /** Биржевое значение. */
    BigDecimal externalValue;
}
