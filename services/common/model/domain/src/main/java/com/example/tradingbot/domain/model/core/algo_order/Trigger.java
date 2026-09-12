package com.example.tradingbot.domain.model.core.algo_order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trigger-механизм algo-order: ноги stop-loss и take-profit (null —
 * соответствующая нога не используется). Раздел модели AlgoOrder. См.
 * docs/models/domain/core/AlgoOrder.md (§Condition-модель).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Trigger {

    /** Нога stop-loss (null — не используется). */
    private TriggerPrice stopLoss;

    /** Нога take-profit (null — не используется). */
    private TriggerPrice takeProfit;
}
