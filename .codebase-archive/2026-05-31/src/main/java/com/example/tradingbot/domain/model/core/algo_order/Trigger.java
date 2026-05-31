package com.example.tradingbot.domain.model.core.algo_order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Trigger {

    /**
     * Триггер stop-loss (SL).
     * Если null — SL не используется.
     */
    private TriggerPrice stopLoss;

    /**
     * Триггер take-profit (TP).
     * Если null — TP не используется.
     */
    private TriggerPrice takeProfit;
}
