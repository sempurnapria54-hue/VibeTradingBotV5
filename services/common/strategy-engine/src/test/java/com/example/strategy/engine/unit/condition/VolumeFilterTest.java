package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.OBV_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicator;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicators;
import static com.example.strategy.engine.unit.condition.ConditionFixture.number;
import static com.example.strategy.engine.unit.condition.ConditionFixture.obv;
import static com.example.strategy.engine.unit.condition.ConditionFixture.price;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
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
 * Объёмный фильтр — группа `U6` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (допустимые формы объёмного операнда —
 * docs/models/domain/aggregate/Strategy.md §Условия; <b>сам предикат —
 * код</b>, §«Дом предиката: восемь типов корпус объявляет бездомными
 * сам», звено {@code #evaluateVolumeFilter}, чей javadoc объявляет
 * семантику МИНИМАЛЬНОЙ и отсылает полную к инкременту авторинга).
 *
 * <p><b>Базовая сборка:</b> раскладка последних — {@code obv = 120};
 * предыдущих — {@code obv = 100}; правило `VOLUME_FILTER_PASSED`, левый
 * операнд — индикатор {@code obv}.
 *
 * <p><b>Предикат читает ЛЕВЫЙ операнд против его собственного прошлого:</b>
 * правая сторона и оператор не читаются вовсе, и абсолютное сравнение
 * объёма грамматикой не разрешено.
 */
class VolumeFilterTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Накопительный объём растёт. */
    @Test
    @DisplayName("U6.1 — базовая сборка: 120 против 100 — истина, объём растёт")
    void u6_1_theBaseAssemblyPasses() {
        assertThat(evaluate("120", "100", null, null)).isTrue();
    }

    /** Пара к U6.1: различает их ровно прошлое. */
    @Test
    @DisplayName("U6.2 — предыдущее 140: ложь")
    void u6_2_aFallingVolumeDoesNotPass() {
        assertThat(evaluate("120", "140", null, null)).isFalse();
    }

    /** Сравнение строгое: равенство ростом не является. */
    @Test
    @DisplayName("U6.3 — предыдущее равно последнему: ложь — сравнение строгое")
    void u6_3_anUnchangedVolumeDoesNotPass() {
        assertThat(evaluate("120", "120", null, null)).isFalse();
    }

    /** Недоступное прошлое гасит фильтр, а не пропускает его. */
    @Test
    @DisplayName("U6.4 — предыдущего значения в раскладке нет: ложь, а не пропуск фильтра")
    void u6_4_anAbsentPastDoesNotWaiveTheFilter() {
        StrategyCondition condition = filter(indicator(OBV_KEY, null), null, null);
        ConditionEvaluationContext context = base()
                .latestIndicators(indicators(OBV_KEY, obv("120")))
                .build();

        assertThat(evaluator.evaluate(condition, context)).isFalse();
    }

    /** Правая сторона и оператор не читаются вовсе — исход тот же, что у U6.1. */
    @Test
    @DisplayName("U6.5 — правый операнд — константа 50, оператор GT: исход тот же, что у U6.1")
    void u6_5_theRightOperandAndTheOperatorAreNotRead() {
        assertThat(evaluate("120", "100", number("50"), StrategyConditionOperator.GT))
                .as("абсолютное сравнение объёма грамматикой не разрешено")
                .isTrue();
        assertThat(evaluate("120", "140", number("50"), StrategyConditionOperator.GT))
                .as("и на падающем объёме правая сторона исхода не меняет")
                .isFalse();
    }

    /**
     * У цены прошлого в контексте нет ни одним полем — та же половина, что
     * у `U5.11`: фильтр на ценовом операнде ложен при ЛЮБОМ прошлом
     * (находка `F-3`).
     */
    @Test
    @DisplayName("U6.6 — левый операнд — цена: ложь при любом прошлом")
    void u6_6_aPriceOperandHasNoPastForTheFilter() {
        StrategyCondition condition = filter(price(StrategyPriceSource.LAST_PRICE), null, null);

        assertThat(evaluator.evaluate(condition, priceContext("120", "100")))
                .as("растущая цена фильтра не проходит: прошлого у неё нет")
                .isFalse();
        assertThat(evaluator.evaluate(condition, priceContext("100", "120")))
                .as("и падающая — тем же основанием")
                .isFalse();
    }

    private Boolean evaluate(String current, String previous, StrategyConditionOperand right,
                             StrategyConditionOperator operator) {
        StrategyCondition condition = filter(indicator(OBV_KEY, null), right, operator);
        ConditionEvaluationContext context = base()
                .latestIndicators(indicators(OBV_KEY, obv(current)))
                .previousIndicators(indicators(OBV_KEY, obv(previous)))
                .build();
        return evaluator.evaluate(condition, context);
    }

    private StrategyCondition filter(StrategyConditionOperand left, StrategyConditionOperand right,
                                     StrategyConditionOperator operator) {
        return condition(rule(StrategyConditionRuleType.VOLUME_FILTER_PASSED, operator, left, right));
    }

    /**
     * Контекст, у которого цена момента объявлена, а «прошлая цена»
     * выразима только раскладкой индикаторов: полем контекста её нет.
     */
    private ConditionEvaluationContext priceContext(String current, String pastNeighbour) {
        return base()
                .price(new BigDecimal(current))
                .latestIndicators(indicators(OBV_KEY, obv(current)))
                .previousIndicators(indicators(OBV_KEY, obv(pastNeighbour)))
                .build();
    }
}
