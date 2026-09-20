package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.indicator;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.rules;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyConditionOperandApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Операнды условия: источник, ссылка, компонент — группа {@code U27}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/models/domain/aggregate/Strategy.md §Условия; справочник
 * компонентов берётся НАСТОЯЩИЙ — он предмет соседнего документа
 * `domain-model-predicates`).
 *
 * <p><b>Разбор операнда ветвится ИСТОЧНИКОМ</b>: у индикаторного
 * проверяются ссылка и компонент, у структурного — ссылка, у ценового —
 * перечень источника цены, у константы — тип значения и само значение.
 * Неразобранный источник выключает все четыре ветви разом.
 *
 * <p><b>Компонент проверяется от ТИПА разрешённого индикатора</b>:
 * неразрешённая ссылка выключает его целиком, и клетка называет это
 * молчание.
 */
class ConditionOperandTest {

    /** Правило сравнения двух скользящих средних — носитель индикаторных операндов. */
    private static final int COMPARE_RULE = 2;

    /** Правило сравнения с константой — носитель константного операнда. */
    private static final int CONSTANT_RULE = 3;

    @Test
    @DisplayName("U27.1 — базовая сборка: операнды правил ссылаются на настройки стратегии")
    void u27_1_theReferenceOperandsResolve() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U27.2 — источник операнда неизвестен: разбор операнда прекращается")
    void u27_2_anUnknownSourceTypeStopsTheOperandTraversal() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel operand = leftOf(request, COMPARE_RULE);
        operand.setIndicatorKey("no_such_indicator");
        operand.setSourceType("ORACLE");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".leftOperand.sourceType: unknown value ORACLE");
    }

    @Test
    @DisplayName("U27.3 — операнд опущен вовсе: нарушений об операнде нет, контракт говорит")
    void u27_3_anAbsentOperandIsGuardedButBreaksTheRuleContract() {
        CreateStrategyApiRequest request = reference();
        rules(entryStep(bull(request))).get(COMPARE_RULE).setLeftOperand(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, "leftOperand.")).isEmpty();
        assertThat(matching(violations, "INDICATOR_COMPARE requires operator and both operands")).hasSize(1);
    }

    @Test
    @DisplayName("U27.4 — источник индикатор, ключ настройки опущен: ссылка обязательна")
    void u27_4_anIndicatorOperandRequiresItsKey() {
        CreateStrategyApiRequest request = reference();
        leftOf(request, COMPARE_RULE).setIndicatorKey(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".leftOperand.indicatorKey is required and must reference a indicator setting");
    }

    @Test
    @DisplayName("U27.5 — ключ настройки индикатора не резолвится")
    void u27_5_anUnknownIndicatorKeyDoesNotResolve() {
        CreateStrategyApiRequest request = reference();
        leftOf(request, COMPARE_RULE).setIndicatorKey("no_such_indicator");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("references unknown indicator setting key no_such_indicator");
    }

    @Test
    @DisplayName("U27.6 — источник цена, источник цены не объявлен: сверка безусловна")
    void u27_6_aPriceOperandChecksItsSourceUnconditionally() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel operand = leftOf(request, COMPARE_RULE);
        operand.setIndicatorKey(null);
        operand.setSourceType("PRICE");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".leftOperand.priceSource: unknown value null");
    }

    @Test
    @DisplayName("U27.7 — источник структура, ключ структуры опущен: ссылка обязательна")
    void u27_7_aStructureOperandRequiresItsKey() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel operand = leftOf(request, COMPARE_RULE);
        operand.setIndicatorKey(null);
        operand.setSourceType("MARKET_STRUCTURE");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".leftOperand.structureKey is required and must reference a market structure setting");
    }

    @Test
    @DisplayName("U27.8 — ключ структуры не резолвится")
    void u27_8_anUnknownStructureKeyDoesNotResolve() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel operand = leftOf(request, COMPARE_RULE);
        operand.setIndicatorKey(null);
        operand.setSourceType("MARKET_STRUCTURE");
        operand.setStructureKey("no_such_structure");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("references unknown market structure setting key no_such_structure");
    }

    @Test
    @DisplayName("U27.9 — источник константа, значение не объявлено")
    void u27_9_aConstantOperandRequiresItsValue() {
        CreateStrategyApiRequest request = reference();
        rightOf(request, CONSTANT_RULE).setValue(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".rightOperand.value is required for CONSTANT operand");
    }

    @Test
    @DisplayName("U27.10 — тип значения константы — неизвестная строка")
    void u27_10_anUnknownConstantValueTypeIsRejected() {
        CreateStrategyApiRequest request = reference();
        rightOf(request, CONSTANT_RULE).setValueType("DECIMAL");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".rightOperand.valueType: unknown value DECIMAL");
    }

    @Test
    @DisplayName("U27.11 — компонент у одно-компонентного индикатора: он не задаётся")
    void u27_11_aSingleComponentIndicatorTakesNoComponent() {
        CreateStrategyApiRequest request = reference();
        leftOf(request, COMPARE_RULE).setIndicatorComponent("MACD_LINE");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".indicatorComponent must not be set for single-component indicator EMA");
    }

    @Test
    @DisplayName("U27.12 — операнд многокомпонентного индикатора без компонента: он обязателен")
    void u27_12_aMultiComponentIndicatorRequiresItsComponent() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "ema_fast_15m").setIndicatorType("MACD");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".indicatorComponent is required for multi-component indicator MACD"))
                .as("на ключ ссылаются операнды обеих трендовых деталей")
                .hasSize(2);
        assertThat(violations.get(0)).contains("allowed: ");
    }

    @Test
    @DisplayName("U27.13 — компонент — неизвестная строка: допустимость для типа не считается")
    void u27_13_anUnknownComponentStopsTheAllowanceCheck() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "ema_fast_15m").setIndicatorType("MACD");
        leftOf(request, COMPARE_RULE).setIndicatorComponent("TRIPLE_LINE");

        List<String> violations = violations(request);

        assertThat(matching(violations, ".indicatorComponent: unknown value TRIPLE_LINE")).hasSize(1);
        assertThat(matching(violations, "is not valid for indicator")).isEmpty();
    }

    @Test
    @DisplayName("U27.14 — компонент стохастика у схождения-расхождения: он не допустим для типа")
    void u27_14_aForeignComponentIsRejectedWithTheAllowedSet() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "ema_fast_15m").setIndicatorType("MACD");
        leftOf(request, COMPARE_RULE).setIndicatorComponent("STOCH_K");

        assertThat(matching(violations(request), ".indicatorComponent STOCH_K is not valid for indicator MACD"))
                .singleElement()
                .asString()
                .contains("allowed: ");
    }

    @Test
    @DisplayName("U27.15 — ключ не резолвится при объявленном компоненте: тип неизвестен")
    void u27_15_anUnresolvedKeySilencesTheComponentCheck() {
        CreateStrategyApiRequest request = reference();
        StrategyConditionOperandApiModel operand = leftOf(request, COMPARE_RULE);
        operand.setIndicatorKey("no_such_indicator");
        operand.setIndicatorComponent("MACD_LINE");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("references unknown indicator setting key no_such_indicator");
    }

    private StrategyConditionOperandApiModel leftOf(CreateStrategyApiRequest request, int ruleIndex) {
        return rules(entryStep(bull(request))).get(ruleIndex).getLeftOperand();
    }

    private StrategyConditionOperandApiModel rightOf(CreateStrategyApiRequest request, int ruleIndex) {
        return rules(entryStep(bull(request))).get(ruleIndex).getRightOperand();
    }
}
