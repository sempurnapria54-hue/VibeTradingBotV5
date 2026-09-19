package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.FAST_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.SLOW_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.constant;
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
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Сравнение: операторы и их область — группа `U2` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (структура правила — docs/rules/strategy-condition-contract.md §«Правило
 * и операнды»; границы контрактов —
 * docs/rules/condition-ruletype-granularity.md §«Границы применения»;
 * форма самого сравнения — §«Дом предиката: восемь типов корпус объявляет
 * бездомными сам», звенья {@code #applyRelational}, {@code #parseConstant}).
 *
 * <p><b>Базовая сборка:</b> раскладка последних значений несёт ключ
 * {@code ema_fast} со значением {@code EMA = 12}; правило
 * `INDICATOR_COMPARE`, левый операнд — индикатор {@code ema_fast}, правый —
 * константа {@code 10} типа `NUMBER`, оператор `GT`.
 *
 * <p><b>Оператор `BETWEEN` (`U2.12`) не прогоняется:</b> правилу не на чем
 * нести вторую границу диапазона — форма невыразима структурой, а не
 * только не реализована (§«Кейсы, не прогоняемые сегодня»).
 */
class ComparisonOperatorTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Строгое «больше» на базовой сборке. */
    @Test
    @DisplayName("U2.1 — базовая сборка: 12 > 10 — истина")
    void u2_1_theBaseAssemblyIsTrue() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT, number("10")))).isTrue();
    }

    /** Вторая сторона той же границы. */
    @Test
    @DisplayName("U2.2 — константа 14: ложь")
    void u2_2_theOtherSideOfTheSameBoundary() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT, number("14")))).isFalse();
    }

    /** Строгий оператор на равенстве не срабатывает. */
    @Test
    @DisplayName("U2.3 — константа 12, оператор GT: ложь — сравнение строгое")
    void u2_3_strictGreaterThanIsFalseOnEquality() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT, number("12")))).isFalse();
    }

    /** Включающая граница — пара к U2.3, и различает их ровно оператор. */
    @Test
    @DisplayName("U2.4 — константа 12, оператор GTE: истина — граница включающая")
    void u2_4_theInclusiveBoundaryAcceptsEquality() {
        assertThat(evaluate(compare(StrategyConditionOperator.GTE, number("12")))).isTrue();
    }

    /** Зеркальная строгая граница. */
    @Test
    @DisplayName("U2.5 — оператор LT, константа 14: истина")
    void u2_5_strictLessThan() {
        assertThat(evaluate(compare(StrategyConditionOperator.LT, number("14")))).isTrue();
    }

    /** Зеркальная включающая граница. */
    @Test
    @DisplayName("U2.6 — оператор LTE, константа 12: истина")
    void u2_6_inclusiveLessThan() {
        assertThat(evaluate(compare(StrategyConditionOperator.LTE, number("12")))).isTrue();
    }

    /** Сравнение идёт по ЗНАЧЕНИЮ, а не по масштабу записи. */
    @Test
    @DisplayName("U2.7 — оператор EQ, константа 12.0: истина — сравнение по значению")
    void u2_7_equalityComparesTheValueNotTheScale() {
        assertThat(evaluate(compare(StrategyConditionOperator.EQ, number("12.0")))).isTrue();
    }

    /** Та же граница с другой стороны — пара к U2.7. */
    @Test
    @DisplayName("U2.8 — оператор NE, константа 12.0: ложь")
    void u2_8_inequalityOnTheSameValue() {
        assertThat(evaluate(compare(StrategyConditionOperator.NE, number("12.0")))).isFalse();
    }

    /**
     * Любой источник допустим на любой стороне.
     *
     * <p><b>Охрана второго рубежа:</b> сравнивающему правилу создание
     * требует операнд названного источника, и правило из двух констант до
     * интерпретатора не доезжает (docs/rules/strategy-validation.md).
     */
    @Test
    @DisplayName("U2.9 — константы на обеих сторонах: 12 против 10 — истина")
    void u2_9_constantsAreAllowedOnBothSides() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE,
                StrategyConditionOperator.GT, number("12"), number("10")));

        assertThat(evaluator.evaluate(condition, context())).isTrue();
    }

    /** Сравнение индикатора с индикатором — базовая форма грамматики. */
    @Test
    @DisplayName("U2.10 — индикатор слева, индикатор справа: 12 против 10 — истина")
    void u2_10_anIndicatorIsComparableWithAnIndicator() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE,
                StrategyConditionOperator.GT, indicator(FAST_KEY, null), indicator(SLOW_KEY, null)));

        assertThat(evaluator.evaluate(condition, context())).isTrue();
    }

    /**
     * Пустой оператор гасится ДО разбора ветвей — ложь, а не падение.
     *
     * <p>Асимметрия названа: у пересечения (`U5.12`) и у равенства
     * структуры (`U7.14`) той же охраны в коде нет.
     */
    @Test
    @DisplayName("U2.11 — оператор пуст: ложь, а не падение")
    void u2_11_anAbsentOperatorIsFalseAndNotAFailure() {
        assertThat(evaluate(compare(null, number("10")))).isFalse();
    }

    /**
     * Пересечение исполняет СВОЯ ветвь, а в сравнивающем правиле оператор
     * не разрешается ни во что.
     *
     * <p><b>Охраны второго рубежа здесь нет:</b> создание оператор
     * сравнивающего правила не сужает вовсе, и такое определение
     * принимается и молча ложно — тот же класс, что у находки `F-6`.
     */
    @Test
    @DisplayName("U2.13 — оператор CROSSED_ABOVE у типа INDICATOR_COMPARE: ложь")
    void u2_13_aCrossingOperatorIsNotResolvedInAComparison() {
        assertThat(evaluate(compare(StrategyConditionOperator.CROSSED_ABOVE, number("10")))).isFalse();
    }

    /** Недоступный операнд консервативно ложен. */
    @Test
    @DisplayName("U2.14 — правый операнд отсутствует: ложь")
    void u2_14_anAbsentRightOperandIsFalse() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT, null))).isFalse();
    }

    /** Тип источника операнда — значение перечня; пустой не резолвится ни во что. */
    @Test
    @DisplayName("U2.15 — у правого операнда тип источника пуст: ложь")
    void u2_15_anOperandWithoutASourceTypeIsFalse() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT, new StrategyConditionOperand()))).isFalse();
    }

    /** В сравнение числовых значений перечисление не разрешается. */
    @Test
    @DisplayName("U2.16 — правая константа типа ENUM: ложь — перечисление числом не сравнивается")
    void u2_16_anEnumConstantIsNotANumber() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT,
                constant("BULL_TREND", ConstantValueType.ENUM)))).isFalse();
    }

    /** Та же ветвь у булева литерала. */
    @Test
    @DisplayName("U2.17 — правая константа типа BOOLEAN: ложь")
    void u2_17_aBooleanConstantIsNotANumber() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT,
                constant("true", ConstantValueType.BOOLEAN)))).isFalse();
    }

    /** Процент читается тем же числом, что и `NUMBER` — пара к U2.16 и U2.17. */
    @Test
    @DisplayName("U2.18 — правая константа типа PERCENT со значением 10: истина")
    void u2_18_aPercentConstantIsReadAsTheSameNumber() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT,
                constant("10", ConstantValueType.PERCENT)))).isTrue();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b>
     * Неразбираемый литерал есть недоступный операнд, и дом объявляет
     * консервативную ложь (docs/rules/absent-value-semantics.md); код
     * бросает исключение разбора и роняет весь тик отбора входа. Красный
     * прогон и есть предъявление находки `F-8` (`.claude/work/backlog.md`
     * §«Интерпретатор бросает там, где объявлена консервативная ложь»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U2.19 — правая константа NUMBER со значением abc: ложь (дом), код бросает разбор")
    void u2_19_anUnparsableLiteralIsAnUnavailableOperand() {
        assertThat(evaluate(compare(StrategyConditionOperator.GT, number("abc"))))
                .as("неразбираемый литерал — недоступный операнд, а не отказ")
                .isFalse();
    }

    /** Тип правила на разрешение операндов не влияет — оба ведут в одно сравнение. */
    @Test
    @DisplayName("U2.20 — тип PRICE_COMPARE, левый операнд — цена 12: истина")
    void u2_20_theRuleTypeDoesNotChangeOperandResolution() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.PRICE_COMPARE,
                StrategyConditionOperator.GT, price(StrategyPriceSource.LAST_PRICE), number("10")));

        assertThat(evaluator.evaluate(condition, base().price(new BigDecimal("12")).build())).isTrue();
    }

    /**
     * Пять операторов перечня, у которых исполнения нет: исход один —
     * молчаливая ложь, и различает их только имя.
     *
     * <p>Строка добрана под-шагом 3 по пробелу `G1`: строка на каждое имя
     * была бы пятикратным повтором одной ветви ({@code #applyRelational},
     * {@code default}), а перечень — предмет параметризации.
     */
    @ParameterizedTest(name = "U2.21 — оператор {0}: ложь без записи журнала")
    @EnumSource(value = StrategyConditionOperator.class,
            names = {"NOT_BETWEEN", "IS_TRUE", "IS_FALSE", "EXISTS", "NOT_EXISTS"})
    @DisplayName("U2.21 — пять операторов без исполнения: ложь, записи журнала нет")
    void u2_21_theUnevaluableOperatorsAreSilentlyFalse(StrategyConditionOperator operator) {
        try (EvaluatorLogCapture log = EvaluatorLogCapture.attach()) {
            assertThat(evaluate(compare(operator, number("10"))))
                    .as("оператор %s объявлен перечнем и не исполняется", operator)
                    .isFalse();

            assertThat(log.messages())
                    .as("молчаливая ложь: журнальная ветвь у оператора своя не заведена")
                    .isEmpty();
        }
    }

    private Boolean evaluate(StrategyCondition condition) {
        return evaluator.evaluate(condition, context());
    }

    private StrategyCondition compare(StrategyConditionOperator operator, StrategyConditionOperand right) {
        return condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE, operator,
                indicator(FAST_KEY, null), right));
    }

    private ConditionEvaluationContext context() {
        return base()
                .latestIndicators(indicators(FAST_KEY, ema("12"), SLOW_KEY, ema("10")))
                .build();
    }
}
