package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.constantOperand;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.indicatorOperand;
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
 * <p><b>Дома у группы нет, и это находка, а не умолчание</b>
 * ({@code F6}): минимальный набор операндов по типу правила не объявлен
 * ни одним доком, модель и контракт авторинга говорят обратное, а
 * javadoc предмета адресует файл, которого в корпусе не существует.
 * Носитель ожиданий поэтому — звено кода
 * {@code StrategyDefinitionValidator#validateRuleContract}.
 *
 * <p><b>Контракт инкрементален:</b> тип, которого он не описывает,
 * нарушения не даёт, и это утверждение о предмете, а не пропуск кейса.
 */
class RuleContractTest {

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

    @Test
    @DisplayName("U28.5 — закрытие свечи без таймфрейма")
    void u28_5_aCandleClosedRuleRequiresItsTimeframe() {
        assertThat(violationsOfRule(newRule("CANDLE_CLOSED")))
                .singleElement()
                .asString()
                .contains("timeframe is required for CANDLE_CLOSED");
    }

    @Test
    @DisplayName("U28.6 — закрытие свечи с таймфреймом: нарушений нет")
    void u28_6_aDeclaredTimeframeSatisfiesTheCandleContract() {
        StrategyConditionRuleApiModel rule = newRule("CANDLE_CLOSED");
        rule.setTimeframe("FIVE_MINUTES");

        assertThat(violationsOfRule(rule)).isEmpty();
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
    @DisplayName("U28.8 — подтверждённый пробой с операндом структуры: нарушений нет")
    void u28_8_aStructureOperandSatisfiesTheBreakoutContract() {
        StrategyConditionRuleApiModel rule = newRule("RANGE_BREAKOUT_CONFIRMED");
        rule.setLeftOperand(structureOperand());

        assertThat(violationsOfRule(rule)).isEmpty();
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

    @Test
    @DisplayName("U28.13 — константа объявлена числовым типом: сверка с перечнем не идёт")
    void u28_13_aNumericConstantIsNotMatchedAgainstTheEnum() {
        StrategyConditionRuleApiModel rule = newRule("MARKET_STRUCTURE_IS");
        rule.setOperator("EQ");
        rule.setLeftOperand(structureOperand());
        rule.setRightOperand(constantOperand("NUMBER", "SQUEEZE"));

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
    @DisplayName("U28.24 — тип правила, которого контракт не описывает: он инкрементален")
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

    /** Нарушения дерева, у которого условие входного шага заменено названным правилом. */
    private List<String> violationsOfRule(StrategyConditionRuleApiModel rule) {
        CreateStrategyApiRequest request = reference();
        replaceRules(entryStep(bull(request)), rule);
        return violations(request);
    }

    private StrategyConditionOperandApiModel structureOperand() {
        StrategyConditionOperandApiModel operand = newOperand("MARKET_STRUCTURE");
        operand.setStructureKey("phase_structure_1h");
        return operand;
    }

    private StrategyConditionOperandApiModel priceOperand() {
        StrategyConditionOperandApiModel operand = newOperand("PRICE");
        operand.setPriceSource("LAST_PRICE");
        return operand;
    }
}
