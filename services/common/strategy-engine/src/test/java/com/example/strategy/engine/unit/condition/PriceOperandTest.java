package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.number;
import static com.example.strategy.engine.unit.condition.ConditionFixture.price;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ценовой операнд — группа `U4` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (docs/models/domain/aggregate/Strategy.md §Условия;
 * docs/components/StrategyConditionEvaluator.md §Данные).
 *
 * <p><b>Базовая сборка:</b> цена момента в контексте {@code 100}; правило
 * `PRICE_COMPARE`, левый операнд — цена, правый — константа {@code 90},
 * оператор `GT`.
 *
 * <p>Строки `U4.4`-`U4.6` сняты: источника цены у ценового операнда нет —
 * он последняя цена сделки, и объявленный источник отвергает создание
 * (docs/rules/strategy-condition-contract.md §«Грамматика объявляет только
 * исполняемое»).
 */
class PriceOperandTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Цена момента резолвится единственным скаляром контекста. */
    @Test
    @DisplayName("U4.1 — базовая сборка: 100 > 90 — истина")
    void u4_1_theBaseAssemblyIsTrue() {
        assertThat(evaluate("100", number("90"), StrategyConditionOperator.GT)).isTrue();
    }

    /** Вторая сторона той же границы. */
    @Test
    @DisplayName("U4.2 — константа 110: ложь")
    void u4_2_theOtherSideOfTheSameBoundary() {
        assertThat(evaluate("100", number("110"), StrategyConditionOperator.GT)).isFalse();
    }

    /**
     * Цена — единственный операнд грамматики, который читается у площадки,
     * и её недоступность консервативна.
     */
    @Test
    @DisplayName("U4.3 — цены момента в контексте нет: ложь")
    void u4_3_anUnavailablePriceIsConservativelyFalse() {
        assertThat(evaluate(null, number("90"), StrategyConditionOperator.GT)).isFalse();
    }

    /** Обе стороны берут ОДИН И ТОТ ЖЕ скаляр — включающая граница это и показывает. */
    @Test
    @DisplayName("U4.7 — цена слева, цена справа, оператор GTE: истина — скаляр один и тот же")
    void u4_7_bothSidesTakeTheSameScalar() {
        assertThat(evaluate("100", price(), StrategyConditionOperator.GTE))
                .as("обе стороны резолвятся в один скаляр — равенство обязано держаться")
                .isTrue();
        assertThat(evaluate("100", price(), StrategyConditionOperator.GT))
                .as("и строгий оператор на нём обязан быть ложен")
                .isFalse();
    }

    private Boolean evaluate(String currentPrice, StrategyConditionOperand right,
                             StrategyConditionOperator operator) {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.PRICE_COMPARE, operator,
                price(), right));
        ConditionEvaluationContext context = base()
                .price(isNull(currentPrice) ? null : new BigDecimal(currentPrice))
                .build();
        return evaluator.evaluate(condition, context);
    }
}
