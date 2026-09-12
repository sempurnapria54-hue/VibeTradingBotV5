package com.example.tradingbot.domain.model.core.algo_order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Условие срабатывания algo-order: ровно один механизм —
 * trigger XOR trailing; type соответствует заполненным полям и
 * AlgoOrder.conditionType. Только условие срабатывания (размер —
 * AlgoOrder.size, closeFraction — strategy/action sizing intent).
 * Персистится jsonb на строке AlgoOrder. См.
 * docs/models/domain/core/AlgoOrder.md (§Condition-модель).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Condition {

    /** Тип условия (совпадает с AlgoOrder.conditionType). */
    private AlgoOrder.ConditionType type;

    /** Trigger-механизм (SL/TP/OCO/PARTIAL_*); null для trailing. */
    private Trigger trigger;

    /** Trailing-механизм (TRAILING_*); null для trigger. */
    private Trailing trailing;
}
