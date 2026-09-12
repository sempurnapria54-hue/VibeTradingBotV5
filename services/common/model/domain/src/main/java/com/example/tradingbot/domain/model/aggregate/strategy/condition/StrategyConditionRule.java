package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Правило условия — единая структура; операнды опциональны (левый /
 * правый / оба / нет — отсутствующий не пишется). Доменные правила —
 * плоские: ruleType + простые поля (percents, timeframe); сравнивающие
 * — operator + симметричные структурированные операнды. Любой источник
 * — на любой стороне (число слева или справа, indicator-vs-indicator —
 * базовый кейс кроссовера). Rule-level sourceType/timeframe-источника и
 * объектные ссылки на настройки убраны — их несёт операнд. Per-ruleType
 * контракт полей дозаполняется инкрементально при реализации каждого
 * ruleType. См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyConditionRule),
 * docs/rules/strategy-condition-contract.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConditionRule {

    /** Порядок проверки внутри условия (ASC); локальный, не глобальный. */
    private Integer level;

    /** Тип правила. */
    private StrategyConditionRuleType ruleType;

    /** Простое процентное поле плоских доменных правил (PROFIT_PERCENTS_REACHED и т. п.). */
    private BigDecimal percents;

    /**
     * Простое поле таймфрейма плоских доменных правил, осмысленных
     * относительно конкретной серии свечей (CANDLE_CLOSED). У
     * сравнивающих правил таймфрейм несёт источник/операнд, не правило.
     */
    private TimeFrame timeframe;

    /** Оператор сравнивающего правила; у плоских доменных правил не пишется. */
    private StrategyConditionOperator operator;

    /** Левый операнд сравнивающего правила. */
    private StrategyConditionOperand leftOperand;

    /** Правый операнд сравнивающего правила. */
    private StrategyConditionOperand rightOperand;
}
