package com.example.tradingbot.persistence.model.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyConditionRuleEntity {

    /**
     * Порядок проверки rule внутри condition.
     */
    private Integer level;

    /**
     * Тип условия, которое проверяет StrategyConditionEvaluator.
     */
    private String ruleType;

    /**
     * Процентный параметр rule, если он нужен конкретному типу условия.
     */
    private BigDecimal percents;
}
