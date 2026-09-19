package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.STRUCTURE_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.enumConstant;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static com.example.strategy.engine.unit.condition.ConditionFixture.structure;
import static com.example.strategy.engine.unit.condition.ConditionFixture.structureOperand;
import static com.example.strategy.engine.unit.condition.ConditionFixture.structureWithBreakout;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Структура рынка: равенство типа и подтверждённый пробой — группа `U7`
 * документа `.claude/tests/cases/strategy-engine-condition.md`
 * (модель структуры и предвычисленное событие пробоя —
 * docs/models/domain/other/MarketStructure.md; границы контрактов —
 * docs/rules/condition-ruletype-granularity.md §«Границы применения»;
 * <b>чтение оператора и сторона операнда — код</b>, §«Дом предиката:
 * восемь типов корпус объявляет бездомными сам»).
 *
 * <p><b>Базовая сборка:</b> раскладка структур несёт ключ
 * {@code range_h1} со структурой типа `RANGE` без события пробоя; правило
 * `MARKET_STRUCTURE_IS`, левый операнд — структура {@code range_h1},
 * правый — константа `RANGE` типа `ENUM`, оператор `EQ`.
 *
 * <p><b>Событие пробоя ПРЕДВЫЧИСЛЕНО</b> и читается готовым: буфер и
 * подтверждение суть параметры резолвера структуры, а не поля условия.
 */
class MarketStructureRuleTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Равенство типа структуры объявленному значению. */
    @Test
    @DisplayName("U7.1 — базовая сборка: тип RANGE равен объявленному — истина")
    void u7_1_theBaseAssemblyIsTrue() {
        assertThat(evaluate(structureIs(StrategyConditionOperator.EQ, "RANGE"), rangeLayout())).isTrue();
    }

    /** Пара к U7.1. */
    @Test
    @DisplayName("U7.2 — константа UPTREND: ложь")
    void u7_2_aDifferentTypeIsFalse() {
        assertThat(evaluate(structureIs(StrategyConditionOperator.EQ, "UPTREND"), rangeLayout())).isFalse();
    }

    /** Отрицание равенства читается. */
    @Test
    @DisplayName("U7.3 — оператор NE, константа UPTREND: истина — отрицание равенства читается")
    void u7_3_theNegationOfEqualityIsRead() {
        assertThat(evaluate(structureIs(StrategyConditionOperator.NE, "UPTREND"), rangeLayout())).isTrue();
    }

    /** Над перечислением упорядочения нет. */
    @Test
    @DisplayName("U7.4 — оператор GT: ложь — над перечислением упорядочения нет")
    void u7_4_thereIsNoOrderingOverAnEnumeration() {
        assertThat(evaluate(structureIs(StrategyConditionOperator.GT, "RANGE"), rangeLayout())).isFalse();
    }

    /** Ключа в раскладке нет — недоступный операнд. */
    @Test
    @DisplayName("U7.5 — ключа range_h1 в раскладке структур нет: ложь")
    void u7_5_aMissingStructureKeyIsFalse() {
        assertThat(evaluate(structureIs(StrategyConditionOperator.EQ, "RANGE"), Map.of())).isFalse();
    }

    /** Структура есть, но её тип не установлен. */
    @Test
    @DisplayName("U7.6 — структура есть, тип у неё пуст: ложь")
    void u7_6_aStructureWithoutATypeIsFalse() {
        assertThat(evaluate(structureIs(StrategyConditionOperator.EQ, "RANGE"),
                Map.of(STRUCTURE_KEY, structure(null)))).isFalse();
    }

    /** Неизвестное значение — ложь, а не падение. Охрана второго рубежа — создание. */
    @Test
    @DisplayName("U7.7 — константа MOONWALK (не значение перечня): ложь, а не падение")
    void u7_7_anUnknownEnumLiteralIsFalse() {
        assertThat(evaluate(structureIs(StrategyConditionOperator.EQ, "MOONWALK"), rangeLayout())).isFalse();
    }

    /** Сторона операнда безразлична ОБЕИМ половинам разбора. */
    @Test
    @DisplayName("U7.8 — структурный операнд справа, константа слева: истина — сторона безразлична")
    void u7_8_theSideOfTheOperandDoesNotMatter() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.MARKET_STRUCTURE_IS,
                StrategyConditionOperator.EQ, enumConstant("RANGE"), structureOperand(STRUCTURE_KEY)));

        assertThat(evaluator.evaluate(condition, context(rangeLayout()))).isTrue();
    }

    /** Предвычисленное событие читается ГОТОВЫМ — интерпретатор его не детектирует. */
    @Test
    @DisplayName("U7.9 — тип RANGE_BREAKOUT_CONFIRMED, событие пробоя есть: истина")
    void u7_9_aPrecomputedBreakoutIsReadAsIs() {
        assertThat(evaluate(breakout(StrategyConditionOperator.EQ), breakoutLayout())).isTrue();
    }

    /** Пара к U7.9. */
    @Test
    @DisplayName("U7.10 — тот же тип, события пробоя у структуры нет: ложь")
    void u7_10_withoutTheEventTheBreakoutIsFalse() {
        assertThat(evaluate(breakout(StrategyConditionOperator.EQ), rangeLayout())).isFalse();
    }

    /** Буфер правилом не читается вовсе — он параметр резолвера структуры. */
    @Test
    @DisplayName("U7.11 — тот же тип, у правила percents = 0.5, события нет: ложь — буфер не поле условия")
    void u7_11_theRulePercentsDoNotOpenTheBreakoutBranch() {
        StrategyConditionRule breakoutRule = rule(StrategyConditionRuleType.RANGE_BREAKOUT_CONFIRMED,
                StrategyConditionOperator.EQ, structureOperand(STRUCTURE_KEY), null);
        breakoutRule.setPercents(new BigDecimal("0.5"));

        assertThat(evaluator.evaluate(condition(breakoutRule), context(rangeLayout()))).isFalse();
    }

    /** Охрана второго рубежа: создание требует у пробоя структурный операнд. */
    @Test
    @DisplayName("U7.12 — тот же тип, структурного операнда нет вовсе: ложь")
    void u7_12_aBreakoutWithoutAStructureOperandIsFalse() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.RANGE_BREAKOUT_CONFIRMED,
                StrategyConditionOperator.EQ, null, null));

        assertThat(evaluator.evaluate(condition, context(breakoutLayout()))).isFalse();
    }

    /** Пробой оператора не знает, и объявленный исхода не меняет. */
    @Test
    @DisplayName("U7.13 — тот же тип, оператор NE: истина — пробой оператора не читает")
    void u7_13_theBreakoutIgnoresTheOperator() {
        assertThat(evaluate(breakout(StrategyConditionOperator.NE), breakoutLayout()))
                .as("исход тот же, что у U7.9: оператор ветвь не выбирает")
                .isTrue();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Структура
     * и константа на месте, ветвиться не на чем, и дом объявляет
     * консервативную ложь (docs/rules/absent-value-semantics.md). Код
     * выбирает ветвь по оператору без охраны — та же асимметрия, что у
     * `U5.12`, при том что сравнение (`U2.11`) ту же охрану несёт. Красный
     * прогон и есть предъявление находки `F-8` (`.claude/work/backlog.md`
     * §«Интерпретатор бросает там, где объявлена консервативная ложь»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U7.14 — базовая сборка, оператор пуст: ложь (дом), код бросает выбор ветви")
    void u7_14_anAbsentOperatorIsFalseAndNotAFailure() {
        assertThat(evaluate(structureIs(null, "RANGE"), rangeLayout()))
                .as("пустой оператор — не на чем ветвиться, а не отказ")
                .isFalse();
    }

    private Boolean evaluate(StrategyCondition condition, Map<String, MarketStructure> structures) {
        return evaluator.evaluate(condition, context(structures));
    }

    private StrategyCondition structureIs(StrategyConditionOperator operator, String declaredType) {
        return condition(rule(StrategyConditionRuleType.MARKET_STRUCTURE_IS, operator,
                structureOperand(STRUCTURE_KEY), enumConstant(declaredType)));
    }

    private StrategyCondition breakout(StrategyConditionOperator operator) {
        return condition(rule(StrategyConditionRuleType.RANGE_BREAKOUT_CONFIRMED, operator,
                structureOperand(STRUCTURE_KEY), null));
    }

    private Map<String, MarketStructure> rangeLayout() {
        return Map.of(STRUCTURE_KEY, structure(MarketStructure.Type.RANGE));
    }

    private Map<String, MarketStructure> breakoutLayout() {
        return Map.of(STRUCTURE_KEY, structureWithBreakout(MarketStructure.Type.RANGE));
    }

    private ConditionEvaluationContext context(Map<String, MarketStructure> structures) {
        return base().structures(structures).build();
    }
}
