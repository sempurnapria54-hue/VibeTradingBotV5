package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.constantOperand;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.indicatorOperand;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newOperand;
import static com.example.strategies.unit.validation.ValidationFixture.newRule;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.replaceRules;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyConditionOperandApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionRuleApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Контракт по типу правила — группа {@code U28} документа
 * `.claude/tests/cases/strategy-definition-validation.md`.
 *
 * <p><b>Дом группы</b> — docs/rules/strategy-condition-contract.md
 * §«Правило и операнды» и §«Грамматика объявляет только исполняемое»: тип
 * правила задаёт его поля ТОЧНО — названное обязательно, неназванное
 * отвергается ({@code STRATEGY_CONDITION_FIELD_NOT_READ}), а операнд
 * сравнения и пересечения скалярен ({@code STRATEGY_CONDITION_OPERAND_NOT_SCALAR}).
 * Тип, чья строка не называет ни одного поля, без полей нарушения не даёт.
 */
class RuleContractTest {

    /** Реджект пересечения с ценой без индикатора-пары. */
    private static final String CROSSOVER_UNPAIRED = "STRATEGY_CROSSOVER_PRICE_WITHOUT_INDICATOR";

    /** Реджект объёмного фильтра на неиндикаторном операнде. */
    private static final String VOLUME_NOT_INDICATOR = "STRATEGY_VOLUME_FILTER_OPERAND_NOT_INDICATOR";

    /** Реджект поля, которого оценка типа не читает. */
    private static final String FIELD_NOT_READ = "STRATEGY_CONDITION_FIELD_NOT_READ";

    /** Реджект нескалярного операнда сравнения либо пересечения. */
    private static final String NOT_SCALAR = "STRATEGY_CONDITION_OPERAND_NOT_SCALAR";

    @Test
    @DisplayName("U28.1 — базовая сборка: правила несут операторы и операнды своего типа")
    void u28_1_theReferenceRulesSatisfyTheirContracts() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U28.2 — пороговый процент прибыли без процента")
    void u28_2_aProfitThresholdRequiresItsPercents() {
        assertThat(violationsOfRule(newRule("PROFIT_PERCENTS_REACHED")))
                .singleElement()
                .asString()
                .contains("percents is required for PROFIT_PERCENTS_REACHED");
    }

    @Test
    @DisplayName("U28.3 — пороговый процент убытка без процента: тот же код со своим типом")
    void u28_3_aLossThresholdRequiresItsPercentsToo() {
        assertThat(violationsOfRule(newRule("LOSS_PERCENTS_REACHED")))
                .singleElement()
                .asString()
                .contains("percents is required for LOSS_PERCENTS_REACHED");
    }

    @Test
    @DisplayName("U28.4 — пороговый процент прибыли с объявленным процентом: нарушений нет")
    void u28_4_aDeclaredPercentsSatisfiesTheThresholdContract() {
        StrategyConditionRuleApiModel rule = newRule("PROFIT_PERCENTS_REACHED");
        rule.setPercents(decimal("1"));

        assertThat(violationsOfRule(rule)).isEmpty();
    }

    /**
     * Тип закрытия свечи снят из перечня вместе с исполнением: операнда
     * закрытия свечи контекст оценки не несёт
     * (docs/rules/strategy-condition-contract.md §«Тип без операнда не
     * объявляется»). Прежнее имя отвергает создание разбором перечня — той
     * же ветвью, что всякое неизвестное (группа {@code U29}), а не
     * контрактом типа. Таймфрейм отвергается сам по себе: поля таймфрейма у
     * правила нет независимо от типа.
     */
    @Test
    @DisplayName("U28.5 — снятый тип закрытия свечи с таймфреймом: имя вне перечня и поле, которого нет")
    void u28_5_theRetiredCandleClosedNameIsAnUnknownRuleType() {
        StrategyConditionRuleApiModel rule = newRule("CANDLE_CLOSED");
        rule.setTimeframe("FIVE_MINUTES");

        List<String> violations = violationsOfRule(rule);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, ".ruleType: unknown value CANDLE_CLOSED")).hasSize(1);
        assertThat(matching(violations, ".timeframe " + FIELD_NOT_READ)).hasSize(1);
    }

    @Test
    @DisplayName("U28.7 — подтверждённый пробой без операнда структуры")
    void u28_7_aBreakoutRuleRequiresAStructureOperand() {
        assertThat(violationsOfRule(newRule("RANGE_BREAKOUT_CONFIRMED")))
                .singleElement()
                .asString()
                .contains("requires a MARKET_STRUCTURE operand (structureKey)");
    }

    @Test
    @DisplayName("U28.8 — подтверждённый пробой с операндом структуры, оператором и направлением: нарушений нет")
    void u28_8_aStructureOperandSatisfiesTheBreakoutContract() {
        StrategyConditionRuleApiModel rule = newRule("RANGE_BREAKOUT_CONFIRMED");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", "UP"));

        assertThat(violationsOfRule(rule)).isEmpty();
    }

    @Test
    @DisplayName("U28.26 — подтверждённый пробой без направления: нарушение «требуются оператор и оба операнда»")
    void u28_26_aBreakoutWithoutADirectionIsRefused() {
        StrategyConditionRuleApiModel rule = newRule("RANGE_BREAKOUT_CONFIRMED");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("RANGE_BREAKOUT_CONFIRMED requires operator and both operands");
    }

    @Test
    @DisplayName("U28.28 — подтверждённый пробой с оператором GT: нарушение «только EQ либо NE»")
    void u28_28_aBreakoutAcceptsOnlyEqualityOperators() {
        StrategyConditionRuleApiModel rule = newRule("RANGE_BREAKOUT_CONFIRMED");
        rule.setOperator("GT");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", "UP"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("RANGE_BREAKOUT_CONFIRMED accepts only EQ or NE");
    }

    @Test
    @DisplayName("U28.27 — направление пробоя вне перечня: нарушение «требуется константа направления»")
    void u28_27_aBreakoutDirectionOutsideTheEnumerationIsRefused() {
        StrategyConditionRuleApiModel rule = newRule("RANGE_BREAKOUT_CONFIRMED");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", "SIDEWAYS"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("requires a CONSTANT operand with the breakout direction");
    }

    @Test
    @DisplayName("U28.9 — утверждение о структуре без оператора: прочие клаузы не считаются")
    void u28_9_aStructureAssertionWithoutAnOperatorStopsEarly() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", "RANGE"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("MARKET_STRUCTURE_IS requires operator and both operands");
    }

    @Test
    @DisplayName("U28.10 — утверждение о структуре, где ни один операнд не структурный")
    void u28_10_aStructureAssertionRequiresAStructureOperand() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(constantOperand("ENUM", "RANGE"));
        rule.setRightOperand(constantOperand("ENUM", "RANGE"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("MARKET_STRUCTURE_IS requires a MARKET_STRUCTURE operand");
    }

    @Test
    @DisplayName("U28.11 — утверждение о структуре без операнда-константы: сверка значения не считается")
    void u28_11_aStructureAssertionRequiresAConstantOperand() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(priceOperand());

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("requires a CONSTANT operand with the structure type");
    }

    @Test
    @DisplayName("U28.12 — константа типа «перечень» со значением вне перечня структур")
    void u28_12_anUnknownStructureTypeConstantIsRejected() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", "SQUEEZE"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("unknown MarketStructure.Type SQUEEZE");
    }

    /**
     * Ожидание перевёрнуто кодовой пачкой 2026-09-30: прежде клетка пинила
     * проход числовой константы — сверка шла только у типа «перечень», — а
     * оценка читает значение строкой, не глядя на тип, и на значении вне
     * перечня правило ложно всегда.
     */
    @Test
    @DisplayName("U28.13 — константа объявлена числовым типом со значением вне перечня: нарушение значения")
    void u28_13_aNumericConstantIsMatchedAgainstTheEnumToo() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("NUMBER", "SQUEEZE"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("unknown MarketStructure.Type SQUEEZE");
    }

    @Test
    @DisplayName("U28.36 — утверждение о структуре с оператором GT: нарушение «только EQ либо NE»")
    void u28_36_aStructureAssertionAcceptsOnlyEqualityOperators() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("GT");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", "RANGE"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("MARKET_STRUCTURE_IS accepts only EQ or NE, got GT");
    }

    @Test
    @DisplayName("U28.39 — константа утверждения о структуре пуста: нарушение значения")
    void u28_39_aBlankStructureConstantIsRejected() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", ""));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("unknown MarketStructure.Type");
    }

    @Test
    @DisplayName("U28.41 — числовой тип значения со значением перечня структур: нарушений нет")
    void u28_41_theValueTypeOfTheStructureConstantIsNotChecked() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("NE");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("NUMBER", "RANGE"));

        assertThat(violationsOfRule(rule)).isEmpty();
    }

    @Test
    @DisplayName("U28.14 — утверждение о фазе без правого операнда")
    void u28_14_aPhaseAssertionRequiresBothOperands() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(newOperand("MARKET_PHASE"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("MARKET_PHASE_IS requires operator and both operands");
    }

    @Test
    @DisplayName("U28.15 — утверждение о фазе без операнда-константы")
    void u28_15_aPhaseAssertionRequiresAConstantOperand() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(newOperand("MARKET_PHASE"));
        rule.setRightOperand(priceOperand());

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("requires a CONSTANT operand with the phase");
    }

    @Test
    @DisplayName("U28.16 — константа фазы вне перечня фаз")
    void u28_16_anUnknownPhaseConstantIsRejected() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(newOperand("MARKET_PHASE"));
        rule.setRightOperand(constantOperand("ENUM", "SIDEWAYS"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("unknown MarketPhase.Type SIDEWAYS");
    }

    @Test
    @DisplayName("U28.17 — утверждение о фазе с годной константой: нарушений нет")
    void u28_17_aWellFormedPhaseAssertionIsLegal() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(newOperand("MARKET_PHASE"));
        rule.setRightOperand(constantOperand("ENUM", "BULL_TREND"));

        assertThat(violationsOfRule(rule)).isEmpty();
    }

    @Test
    @DisplayName("U28.37 — утверждение о фазе с оператором GT: нарушение «только EQ либо NE»")
    void u28_37_aPhaseAssertionAcceptsOnlyEqualityOperators() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("GT");
        rule.setLeftOperand(newOperand("MARKET_PHASE"));
        rule.setRightOperand(constantOperand("ENUM", "BULL_TREND"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("MARKET_PHASE_IS accepts only EQ or NE, got GT");
    }

    @Test
    @DisplayName("U28.38 — константа фазы числового типа со значением вне перечня: нарушение значения")
    void u28_38_aNumericPhaseConstantIsMatchedAgainstTheEnum() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(newOperand("MARKET_PHASE"));
        rule.setRightOperand(constantOperand("NUMBER", "5"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("unknown MarketPhase.Type 5");
    }

    @Test
    @DisplayName("U28.40 — утверждение о фазе с оператором NE и годной константой: нарушений нет")
    void u28_40_aNegatedPhaseAssertionIsLegal() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("NE");
        rule.setLeftOperand(newOperand("MARKET_PHASE"));
        rule.setRightOperand(constantOperand("ENUM", "BEAR_TREND"));

        assertThat(violationsOfRule(rule)).isEmpty();
    }

    @Test
    @DisplayName("U28.18 — сравнение индикаторов без правого операнда: требование источника молчит")
    void u28_18_aComparisonWithoutBothOperandsStopsEarly() {
        StrategyConditionRuleApiModel rule = newRule("INDICATOR_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("INDICATOR_COMPARE requires operator and both operands");
    }

    @Test
    @DisplayName("U28.19 — сравнение индикаторов без индикаторного операнда")
    void u28_19_anIndicatorComparisonRequiresAnIndicatorOperand() {
        StrategyConditionRuleApiModel rule = newRule("INDICATOR_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(priceOperand());
        rule.setRightOperand(constantOperand("NUMBER", "1"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("requires an operand with sourceType INDICATOR");
    }

    @Test
    @DisplayName("U28.20 — сравнение цен без ценового операнда: тот же код со своим источником")
    void u28_20_aPriceComparisonRequiresAPriceOperand() {
        StrategyConditionRuleApiModel rule = newRule("PRICE_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(constantOperand("NUMBER", "1"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("requires an operand with sourceType PRICE");
    }

    @Test
    @DisplayName("U28.21 — сравнение цен: ценовой операнд и константа — требуется ОДИН нужный")
    void u28_21_oneOperandOfTheRequiredSourceIsEnough() {
        StrategyConditionRuleApiModel rule = newRule("PRICE_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(priceOperand());
        rule.setRightOperand(constantOperand("NUMBER", "1"));

        assertThat(violationsOfRule(rule)).isEmpty();
    }

    @Test
    @DisplayName("U28.22 — пересечение без операндов")
    void u28_22_aCrossoverRequiresBothOperands() {
        StrategyConditionRuleApiModel rule = newRule("CROSSOVER");
        rule.setOperator("CROSSED_ABOVE");

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("CROSSOVER requires operator and both operands");
    }

    @Test
    @DisplayName("U28.23 — пересечение с оператором «больше»: нужен пересекающий оператор")
    void u28_23_aCrossoverRequiresACrossingOperator() {
        StrategyConditionRuleApiModel rule = newRule("CROSSOVER");
        rule.setOperator("GT");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(indicatorOperand("ema_slow_15m"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("CROSSOVER requires operator CROSSED_ABOVE or CROSSED_BELOW");
    }

    @Test
    @DisplayName("U28.29 — пересечение цены с константой: прошлого у цены нет — отказ пары")
    void u28_29_aPriceCrossingAConstantIsRefused() {
        assertThat(violationsOfRule(crossover(priceOperand(), constantOperand("NUMBER", "100"))))
                .singleElement()
                .asString()
                .contains(CROSSOVER_UNPAIRED);
    }

    @Test
    @DisplayName("U28.30 — пересечение цены с ценой: отказ один на правило, а не на сторону")
    void u28_30_aPriceCrossingAPriceIsRefusedOnce() {
        assertThat(violationsOfRule(crossover(priceOperand(), priceOperand())))
                .singleElement()
                .asString()
                .contains(CROSSOVER_UNPAIRED);
    }

    @Test
    @DisplayName("U28.31 — цена справа, константа слева: сторона цены на исход не влияет")
    void u28_31_thePriceSideDoesNotMatter() {
        assertThat(violationsOfRule(crossover(constantOperand("NUMBER", "100"), priceOperand())))
                .singleElement()
                .asString()
                .contains(CROSSOVER_UNPAIRED);
    }

    @Test
    @DisplayName("U28.32 — пересечение цены с индикатором: прошлое цены задаёт пара — нарушений нет")
    void u28_32_aPriceCrossingAnIndicatorIsLegal() {
        assertThat(violationsOfRule(crossover(priceOperand(), indicatorOperand("ema_fast_15m")))).isEmpty();
    }

    @Test
    @DisplayName("U28.33 — объёмный фильтр на индикаторе: нарушений нет")
    void u28_33_aVolumeFilterOnAnIndicatorIsLegal() {
        StrategyConditionRuleApiModel rule = newRule("VOLUME_FILTER_PASSED");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));

        assertThat(violationsOfRule(rule)).isEmpty();
    }

    @Test
    @DisplayName("U28.34 — объёмный фильтр на цене: прошлого у левого операнда нет — отказ")
    void u28_34_aVolumeFilterOnThePriceIsRefused() {
        StrategyConditionRuleApiModel rule = newRule("VOLUME_FILTER_PASSED");
        rule.setLeftOperand(priceOperand());

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".leftOperand " + VOLUME_NOT_INDICATOR);
    }

    @Test
    @DisplayName("U28.35 — объёмный фильтр без операнда: то же состояние и тот же код")
    void u28_35_aVolumeFilterWithoutItsOperandIsRefused() {
        assertThat(violationsOfRule(newRule("VOLUME_FILTER_PASSED")))
                .singleElement()
                .asString()
                .contains(VOLUME_NOT_INDICATOR);
    }

    @Test
    @DisplayName("U28.24 — тип без требований контракта")
    void u28_24_anUndescribedRuleTypeCarriesNoContract() {
        assertThat(violationsOfRule(newRule("TREND_CHANGED"))).isEmpty();
    }

    @Test
    @DisplayName("U28.25 — тип правила — неизвестная строка: контракт не считается вовсе")
    void u28_25_anUnknownRuleTypeStopsTheContractCheck() {
        assertThat(violationsOfRule(newRule("MOON_PHASE")))
                .singleElement()
                .asString()
                .contains(".ruleType: unknown value MOON_PHASE");
    }

    @Test
    @DisplayName("U28.42 — сравнение индикаторов с оператором CROSSED_ABOVE: оператор сравнения — одно из шести")
    void u28_42_aComparisonAcceptsOnlyAComparisonOperator() {
        StrategyConditionRuleApiModel rule = newRule("INDICATOR_COMPARE");
        rule.setOperator("CROSSED_ABOVE");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(indicatorOperand("ema_slow_15m"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("INDICATOR_COMPARE accepts only a comparison operator")
                .contains("got CROSSED_ABOVE");
    }

    @Test
    @DisplayName("U28.43 — сравнение индикатора с константой-перечнем: операнд не скалярен")
    void u28_43_anEnumConstantIsNotAComparisonScalar() {
        StrategyConditionRuleApiModel rule = newRule("INDICATOR_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(constantOperand("ENUM", "BULL_TREND"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".rightOperand " + NOT_SCALAR);
    }

    @Test
    @DisplayName("U28.44 — сравнение цены с операндом структуры: операнд не скалярен")
    void u28_44_aStructureOperandIsNotAComparisonScalar() {
        StrategyConditionRuleApiModel rule = newRule("PRICE_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(priceOperand());
        rule.setRightOperand(structureOperand());

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".rightOperand " + NOT_SCALAR);
    }

    @Test
    @DisplayName("U28.45 — пересечение индикатора с операндом фазы: операнд не скалярен")
    void u28_45_aPhaseOperandIsNotACrossoverScalar() {
        assertThat(violationsOfRule(crossover(indicatorOperand("ema_fast_15m"), newOperand("MARKET_PHASE"))))
                .singleElement()
                .asString()
                .contains(".rightOperand " + NOT_SCALAR);
    }

    @Test
    @DisplayName("U28.46 — утверждение о фазе с индикатором вместо операнда фазы: требуется операнд фазы")
    void u28_46_aPhaseAssertionRequiresAPhaseOperand() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_PHASE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(constantOperand("ENUM", "BULL_TREND"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains("MARKET_PHASE_IS requires a MARKET_PHASE operand");
    }

    @Test
    @DisplayName("U28.47 — объёмный фильтр с оператором и правым операндом: оба поля не читаются")
    void u28_47_aVolumeFilterReadsNeitherOperatorNorRightOperand() {
        StrategyConditionRuleApiModel rule = newRule("VOLUME_FILTER_PASSED");
        rule.setOperator("GT");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(constantOperand("NUMBER", "1"));

        List<String> violations = violationsOfRule(rule);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, ".operator " + FIELD_NOT_READ)).hasSize(1);
        assertThat(matching(violations, ".rightOperand " + FIELD_NOT_READ)).hasSize(1);
    }

    @Test
    @DisplayName("U28.48 — смена тренда с операндом-константой фазы: операнд не читается")
    void u28_48_aTrendChangeReadsNoOperand() {
        StrategyConditionRuleApiModel rule = newRule("TREND_CHANGED");
        rule.setLeftOperand(constantOperand("ENUM", "RANGE"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".leftOperand " + FIELD_NOT_READ);
    }

    @Test
    @DisplayName("U28.49 — порог прибыли с процентом и операндом-константой: порог читается только полем")
    void u28_49_aProfitThresholdReadsNoOperand() {
        StrategyConditionRuleApiModel rule = newRule("PROFIT_PERCENTS_REACHED");
        rule.setPercents(decimal("2"));
        rule.setRightOperand(constantOperand("NUMBER", "2"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".rightOperand " + FIELD_NOT_READ);
    }

    @Test
    @DisplayName("U28.50 — подтверждённый пробой с процентом: буфер — параметр резолвера, не поле условия")
    void u28_50_aBreakoutReadsNoPercents() {
        StrategyConditionRuleApiModel rule = newRule("RANGE_BREAKOUT_CONFIRMED");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("ENUM", "UP"));
        rule.setPercents(decimal("0.5"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".percents " + FIELD_NOT_READ);
    }

    @Test
    @DisplayName("U28.51 — годное сравнение индикаторов с таймфреймом правила: поля таймфрейма нет")
    void u28_51_aRuleTimeframeIsNotRead() {
        StrategyConditionRuleApiModel rule = newRule("INDICATOR_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(indicatorOperand("ema_slow_15m"));
        rule.setTimeframe("FIFTEEN_MINUTES");

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".timeframe " + FIELD_NOT_READ);
    }

    @Test
    @DisplayName("U28.52 — индикаторный операнд с ключом структуры: поле не читается")
    void u28_52_anIndicatorOperandReadsNoStructureKey() {
        StrategyConditionOperandApiModel indicator = indicatorOperand("ema_fast_15m");
        indicator.setStructureKey("phase_structure_1h");
        StrategyConditionRuleApiModel rule = newRule("INDICATOR_COMPARE");
        rule.setOperator("GT");
        rule.setLeftOperand(indicator);
        rule.setRightOperand(indicatorOperand("ema_slow_15m"));

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".leftOperand.structureKey " + FIELD_NOT_READ);
    }

    @Test
    @DisplayName("U28.53 — операнд-константа с ключом индикатора: поле не читается")
    void u28_53_aConstantOperandReadsNoIndicatorKey() {
        StrategyConditionOperandApiModel constant = constantOperand("NUMBER", "50");
        constant.setIndicatorKey("ema_slow_15m");
        StrategyConditionRuleApiModel rule = newRule("INDICATOR_COMPARE");
        rule.setOperator("GTE");
        rule.setLeftOperand(indicatorOperand("ema_fast_15m"));
        rule.setRightOperand(constant);

        assertThat(violationsOfRule(rule))
                .singleElement()
                .asString()
                .contains(".rightOperand.indicatorKey " + FIELD_NOT_READ);
    }

    /** Нарушения дерева, у которого условие входного шага заменено названным правилом. */
    private List<String> violationsOfRule(StrategyConditionRuleApiModel rule) {
        CreateStrategyApiRequest request = reference();
        replaceRules(entryStep(bull(request)), rule);
        return violations(request);
    }

    /** Пересечение вверх с названными операндами — оператор годен, предмет клетки в операндах. */
    private StrategyConditionRuleApiModel crossover(StrategyConditionOperandApiModel left,
                                                    StrategyConditionOperandApiModel right) {
        StrategyConditionRuleApiModel rule = newRule("CROSSOVER");
        rule.setOperator("CROSSED_ABOVE");
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return rule;
    }

    private StrategyConditionOperandApiModel structureOperand() {
        StrategyConditionOperandApiModel operand = newOperand("MARKET_STRUCTURE");
        operand.setStructureKey("phase_structure_1h");
        return operand;
    }

    /** Ценовой операнд — последняя цена сделки; полей у него нет. */
    private StrategyConditionOperandApiModel priceOperand() {
        return newOperand("PRICE");
    }
}
