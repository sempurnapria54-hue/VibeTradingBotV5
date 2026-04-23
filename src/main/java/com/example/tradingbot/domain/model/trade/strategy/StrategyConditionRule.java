package com.example.tradingbot.domain.model.trade.strategy;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Одно правило внутри общего condition стратегии.
 */
@Getter
@Setter
public class StrategyConditionRule {

    /**
     * Порядок проверки правила внутри condition.
     */
    private Integer level;

    /**
     * Тип правила.
     */
    private StrategyConditionRuleType ruleType;

    /**
     * Универсальный процентный параметр.
     * Используется только если ruleType этого требует.
     */
    private BigDecimal percents;
}
