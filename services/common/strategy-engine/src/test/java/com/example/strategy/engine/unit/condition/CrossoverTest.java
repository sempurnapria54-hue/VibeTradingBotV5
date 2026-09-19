package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.FAST_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.SLOW_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.ema;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicator;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicators;
import static com.example.strategy.engine.unit.condition.ConditionFixture.number;
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
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Пересечение: две половины времени — группа `U5` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (основание именованного типа —
 * docs/rules/condition-ruletype-granularity.md, темпоральная семантика;
 * <b>самих границ пересечения не называет ни один док</b> — §«Дом
 * предиката: восемь типов корпус объявляет бездомными сам», звено
 * {@code #evaluateCrossover}).
 *
 * <p><b>Базовая сборка:</b> раскладка последних значений —
 * {@code ema_fast = 12}, {@code ema_slow = 10}; раскладка предыдущих —
 * {@code ema_fast = 9}, {@code ema_slow = 10}; правило `CROSSOVER`, левый
 * операнд {@code ema_fast}, правый {@code ema_slow}, оператор
 * `CROSSED_ABOVE`.
 *
 * <p><b>Границы у половин времени РАЗНЫЕ, и это предмет группы:</b>
 * прошлое сравнивается включающей границей (касание с последующим уходом
 * есть пересечение), настоящее — строгой.
 */
class CrossoverTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Прежде не выше, теперь строго выше. */
    @Test
    @DisplayName("U5.1 — базовая сборка: прошлое 9 ≤ 10, настоящее 12 > 10 — истина")
    void u5_1_theBaseAssemblyCrossesUpward() {
        assertThat(evaluate("12", "9", StrategyConditionOperator.CROSSED_ABOVE)).isTrue();
    }

    /** Пересечения не произошло — пара к U5.1, различает их ровно прошлое. */
    @Test
    @DisplayName("U5.2 — прошлое 11 (уже было выше): ложь — пересечения не произошло")
    void u5_2_anAlreadyHigherPastIsNotACrossing() {
        assertThat(evaluate("12", "11", StrategyConditionOperator.CROSSED_ABOVE)).isFalse();
    }

    /** Граница ПРОШЛОГО включающая: касание с последующим уходом вверх есть пересечение. */
    @Test
    @DisplayName("U5.3 — прошлые значения равны: истина — граница прошлого включающая")
    void u5_3_theBoundaryOfThePastIsInclusive() {
        assertThat(evaluate("12", "10", StrategyConditionOperator.CROSSED_ABOVE)).isTrue();
    }

    /** Граница НАСТОЯЩЕГО строгая — пара к U5.3, и различает их половина времени. */
    @Test
    @DisplayName("U5.4 — последние значения равны: ложь — граница настоящего строгая")
    void u5_4_theBoundaryOfThePresentIsStrict() {
        assertThat(evaluate("10", "9", StrategyConditionOperator.CROSSED_ABOVE)).isFalse();
    }

    /** Зеркальная форма. */
    @Test
    @DisplayName("U5.5 — CROSSED_BELOW, настоящее 9, прошлое 11: истина")
    void u5_5_theMirroredFormCrossesDownward() {
        assertThat(evaluate("9", "11", StrategyConditionOperator.CROSSED_BELOW)).isTrue();
    }

    /** Та же включающая граница с другой стороны — пара к U5.5, различает их прошлое. */
    @Test
    @DisplayName("U5.6 — CROSSED_BELOW, настоящее 9, прошлое равно 10: истина")
    void u5_6_theInclusivePastHoldsForTheMirroredForm() {
        assertThat(evaluate("9", "10", StrategyConditionOperator.CROSSED_BELOW)).isTrue();
    }

    /** Охрана второго рубежа: создание требует у пересечения один из двух его операторов. */
    @Test
    @DisplayName("U5.7 — оператор GT у типа CROSSOVER: ложь")
    void u5_7_aRelationalOperatorIsNotACrossing() {
        assertThat(evaluate("12", "9", StrategyConditionOperator.GT)).isFalse();
    }

    /** Без второй половины предикат консервативно ложен, и шаг не исполняется. */
    @Test
    @DisplayName("U5.8 — в раскладке предыдущих нет ключа ema_fast: ложь")
    void u5_8_aMissingPastKeyIsConservativelyFalse() {
        StrategyCondition condition = crossover(indicator(FAST_KEY, null), indicator(SLOW_KEY, null),
                StrategyConditionOperator.CROSSED_ABOVE);
        ConditionEvaluationContext context = base()
                .latestIndicators(indicators(FAST_KEY, ema("12"), SLOW_KEY, ema("10")))
                .previousIndicators(indicators(SLOW_KEY, ema("10")))
                .build();

        assertThat(evaluator.evaluate(condition, context)).isFalse();
    }

    /** Та же ветвь у пустой раскладки целиком. */
    @Test
    @DisplayName("U5.9 — раскладка предыдущих пуста целиком: ложь")
    void u5_9_anEmptyPastLayoutIsConservativelyFalse() {
        StrategyCondition condition = crossover(indicator(FAST_KEY, null), indicator(SLOW_KEY, null),
                StrategyConditionOperator.CROSSED_ABOVE);
        ConditionEvaluationContext context = base()
                .latestIndicators(indicators(FAST_KEY, ema("12"), SLOW_KEY, ema("10")))
                .build();

        assertThat(evaluator.evaluate(condition, context)).isFalse();
    }

    /** У константы прошлое равно настоящему, и пересечение с УРОВНЕМ выразимо. */
    @Test
    @DisplayName("U5.10 — правый операнд — константа 10: истина — у константы половины времени не различаются")
    void u5_10_aConstantIsItsOwnPast() {
        StrategyCondition condition = crossover(indicator(FAST_KEY, null), number("10"),
                StrategyConditionOperator.CROSSED_ABOVE);
        ConditionEvaluationContext context = base()
                .latestIndicators(indicators(FAST_KEY, ema("12")))
                .previousIndicators(indicators(FAST_KEY, ema("9")))
                .build();

        assertThat(evaluator.evaluate(condition, context)).isTrue();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * объявляет любой источник допустимым на любой стороне
     * (docs/models/domain/aggregate/Strategy.md §Условия), то есть форма
     * «цена пересекла среднюю» грамматикой выразима и создание её
     * пропускает. Предыдущей цены контекст не несёт ни одним полем, и
     * пересечение с участием ценового операнда ложно при ЛЮБОМ входе.
     * Красный прогон и есть предъявление находки `F-3`
     * (`.claude/work/backlog.md` §«Кроссовер с ценовым операндом ложен
     * всегда: предыдущей цены в контексте нет»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U5.11 — цена слева пересекла среднюю снизу вверх: истина (дом), код ложен при любом прошлом")
    void u5_11_aPriceOperandCanNeverCross() {
        StrategyCondition condition = crossover(price(StrategyPriceSource.LAST_PRICE), indicator(SLOW_KEY, null),
                StrategyConditionOperator.CROSSED_ABOVE);
        ConditionEvaluationContext context = base()
                .price(new BigDecimal("100"))
                .latestIndicators(indicators(SLOW_KEY, ema("90")))
                .previousIndicators(indicators(SLOW_KEY, ema("110")))
                .build();

        assertThat(evaluator.evaluate(condition, context))
                .as("цена была под средней и ушла над ней — пересечение состоялось")
                .isTrue();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b>
     * Недоступного операнда здесь нет — обе половины обоих операндов
     * доступны, — и ветвиться не на чем: дом объявляет консервативную ложь
     * (docs/rules/absent-value-semantics.md). Код выбирает ветвь по
     * оператору БЕЗ охраны, в отличие от сравнения (`U2.11`), и на пустом
     * бросает. Красный прогон и есть предъявление находки `F-8`
     * (`.claude/work/backlog.md` §«Интерпретатор бросает там, где
     * объявлена консервативная ложь»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U5.12 — оператор пуст, обе половины доступны: ложь (дом), код бросает выбор ветви")
    void u5_12_anAbsentOperatorIsFalseAndNotAFailure() {
        assertThat(evaluate("12", "9", null))
                .as("асимметрия с U2.11: у сравнения та же охрана есть")
                .isFalse();
    }

    private Boolean evaluate(String currentFast, String previousFast, StrategyConditionOperator operator) {
        StrategyCondition condition = crossover(indicator(FAST_KEY, null), indicator(SLOW_KEY, null), operator);
        return evaluator.evaluate(condition, context(currentFast, previousFast));
    }

    private StrategyCondition crossover(StrategyConditionOperand left, StrategyConditionOperand right,
                                        StrategyConditionOperator operator) {
        return condition(rule(StrategyConditionRuleType.CROSSOVER, operator, left, right));
    }

    private ConditionEvaluationContext context(String currentFast, String previousFast) {
        Map<String, IndicatorValue> latest = indicators(FAST_KEY, ema(currentFast), SLOW_KEY, ema("10"));
        Map<String, IndicatorValue> previous = indicators(FAST_KEY, ema(previousFast), SLOW_KEY, ema("10"));
        return base().latestIndicators(latest).previousIndicators(previous).build();
    }
}
