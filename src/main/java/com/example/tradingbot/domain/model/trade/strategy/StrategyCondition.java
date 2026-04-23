package com.example.tradingbot.domain.model.trade.strategy;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * StrategyCondition — это набор rules.
 * <p>
 * Все rules внутри одного condition должны быть истинны.
 */
@Getter
@Setter
public class StrategyCondition {

    /**
     * Rules проверяются по level ASC.
     */
    private List<StrategyConditionRule> rules;
}
