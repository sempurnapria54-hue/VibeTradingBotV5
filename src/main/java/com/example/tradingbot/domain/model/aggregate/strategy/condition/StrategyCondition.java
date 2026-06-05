package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Общее условие применимости шага стратегии: все rules должны быть
 * истинны; проверяются по level ASC (локальный порядок внутри
 * условия, не глобальный порядок шагов). Персистится целиком
 * JSONB-полем condition на строке strategy_step; вычисление
 * истинности — деталь evaluator'а (downstream,
 * StrategyConditionEvaluator). См.
 * docs/models/domain/aggregate/Strategy.md (§Условия),
 * docs/decisions/strategy-condition-authoring-contract.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyCondition {

    /** Правила условия; шаг применим, когда истинны все. */
    private List<StrategyConditionRule> rules;
}
