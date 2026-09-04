package com.example.tradingbot.domain.model.core.algo_order;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Триггерная цена ноги algo-order: внутренний тип/значение и биржевые
 * type/value (могут отличаться округлением). Раздел модели AlgoOrder
 * (model-granularity). См. docs/models/domain/core/AlgoOrder.md
 * (§Condition-модель).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TriggerPrice {

    /** Внутренний тип trigger-цены. */
    private AlgoOrder.TriggerPriceType type;

    /** Внутреннее значение цены. */
    private BigDecimal value;

    /** Биржевой тип цены. */
    private String externalType;

    /** Биржевое значение (может отличаться округлением). */
    private BigDecimal externalValue;
}
