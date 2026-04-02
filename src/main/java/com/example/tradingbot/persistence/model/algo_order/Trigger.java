package com.example.tradingbot.persistence.model.algo_order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trigger {

    /**
     * Триггер stop-loss (SL).
     */
    private TriggerPrice stopLoss;

    /**
     * Триггер take-profit (TP).
     */
    private TriggerPrice takeProfit;
}