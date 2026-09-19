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
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
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
 * `PRICE_COMPARE`, левый операнд — цена с источником `LAST_PRICE`, правый —
 * константа {@code 90}, оператор `GT`.
 *
 * <p><b>Три строки группы здесь не прогоняются</b> (`U4.4`-`U4.6`), и
 * причина у них общая: дом объявляет у ценового операнда источник из
 * перечня размещения, то есть требует величину (бид, аск, марк), которой
 * контекст оценки не несёт ни одним полем — у него один скаляр. Вход, на
 * котором ожидание проверяемо, не выразим ФОРМОЙ контекста, а не просто не
 * реализован (находка `F-2`, §«Кейсы, не прогоняемые сегодня»).
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
        assertThat(evaluate("100", price(StrategyPriceSource.LAST_PRICE), StrategyConditionOperator.GTE))
                .as("обе стороны резолвятся в один скаляр — равенство обязано держаться")
                .isTrue();
        assertThat(evaluate("100", price(StrategyPriceSource.LAST_PRICE), StrategyConditionOperator.GT))
                .as("и строгий оператор на нём обязан быть ложен")
                .isFalse();
    }

    private Boolean evaluate(String currentPrice, StrategyConditionOperand right,
                             StrategyConditionOperator operator) {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.PRICE_COMPARE, operator,
                price(StrategyPriceSource.LAST_PRICE), right));
        ConditionEvaluationContext context = base()
                .price(isNull(currentPrice) ? null : new BigDecimal(currentPrice))
                .build();
        return evaluator.evaluate(condition, context);
    }
}
