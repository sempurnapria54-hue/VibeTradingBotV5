package com.example.tradingbot.domain.model.aggregate.strategy.condition;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Правило условия — единая структура на все типы правил: тип, оператор,
 * левый и правый операнды, процент. Какие из этих полей правило несёт,
 * задаёт его тип — и задаёт точно: правило без названного поля и правило
 * с полем, которого тип не называет, отвергает создание стратегии. Дом
 * контракта — docs/rules/strategy-condition-contract.md §«Правило и
 * операнды»; см. также docs/models/domain/aggregate/Strategy.md
 * (§Условия).
 *
 * <p>Таймфрейма у правила нет: таймфрейм — у настройки индикатора либо
 * структуры, на которую операнд ссылается ключом
 * (docs/rules/strategy-condition-contract.md §«Грамматика объявляет только
 * исполняемое»).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConditionRule {

    /**
     * Поле порядка, общее у всех типов. Ответ конъюнкции от порядка правил
     * не зависит (docs/components/StrategyConditionEvaluator.md §Границы).
     */
    private Integer level;

    /** Тип правила — задаёт, какие из прочих полей правило несёт. */
    private StrategyConditionRuleType ruleType;

    /** Порог в процентах хода от цены входа (PROFIT_PERCENTS_REACHED, LOSS_PERCENTS_REACHED). */
    private BigDecimal percents;

    /** Оператор — у сравнения, пересечения и утверждений над перечнем (пробой, структура, фаза). */
    private StrategyConditionOperator operator;

    /** Левый операнд правила, чей тип операнды называет. */
    private StrategyConditionOperand leftOperand;

    /** Правый операнд правила, чей тип называет оба операнда. */
    private StrategyConditionOperand rightOperand;
}
