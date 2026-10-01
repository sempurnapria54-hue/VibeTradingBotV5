package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Правило условия — единая структура на все типы правил: доменные
 * правила — плоские (ruleType и простые поля), сравнивающие — operator и
 * структурированные операнды. Какие поля и операнды обязательны у
 * каждого типа правила и какие источники допустимы на каждой стороне —
 * контракт по типу правила, его дом — docs/rules/strategy-condition-contract.md
 * §«Правило и операнды». См. также docs/models/domain/aggregate/Strategy.md
 * (§StrategyConditionRule).
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
     * относительно конкретной серии свечей. Сегодня его не читает ни один
     * тип правила: единственный такой тип снят из перечня вместе с
     * исполнением (docs/rules/strategy-condition-contract.md). У
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
