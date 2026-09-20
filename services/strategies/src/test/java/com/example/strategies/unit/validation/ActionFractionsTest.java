package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newOrder;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
import static com.example.strategies.unit.validation.ValidationFixture.newTranche;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.tranches;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Доли объявления: обязательность аллокации и диапазон обеих долей —
 * группа {@code U17} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»).
 *
 * <p><b>Обязательность и диапазон — РАЗНЫЕ проверки с разными кодами.</b>
 * Пустая доля диапазонного кода не поднимает, а нулевая обязательности
 * не нарушает; смешав их, кейс был бы зелен у валидатора, потерявшего
 * одну из двух.
 *
 * <p><b>Долей у действия две, и коды у них тоже разные:</b> отказ
 * адресует ту долю, которая негодна.
 */
class ActionFractionsTest {

    private static final String ALLOCATION_NOT_DECLARED = "STRATEGY_ACTION_ALLOCATION_NOT_DECLARED";

    private static final String ALLOCATION_RANGE = "STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE";

    private static final String FRACTION_RANGE = "STRATEGY_ACTION_FRACTION_NOT_POSITIVE";

    private static final String COVERAGE = "STRATEGY_PROTECTION_COVERAGE_INCOMPLETE";

    private static final String HEADROOM = "STRATEGY_NOTIONAL_HEADROOM_INSUFFICIENT";

    @Test
    @DisplayName("U17.1 — базовая сборка: нарушений о долях нет")
    void u17_1_theReferenceDeclaresLegalFractions() {
        List<String> violations = violations(reference());

        assertThat(matching(violations, ALLOCATION_RANGE)).isEmpty();
        assertThat(matching(violations, FRACTION_RANGE)).isEmpty();
    }

    @Test
    @DisplayName("U17.2 — доля аллокации опущена: обязательность и диапазон разведены")
    void u17_2_anAbsentAllocationRaisesOnlyTheDeclarationCode() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, ALLOCATION_NOT_DECLARED)).hasSize(1);
        assertThat(matching(violations, ALLOCATION_RANGE)).isEmpty();
    }

    @Test
    @DisplayName("U17.3 — доля опущена у входов обоих объявлений: два пути одного кода")
    void u17_3_everyEntryReportsItsOwnMissingAllocation() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(null);
        tranches(bull(request)).add(entryTranche("bull_second", null));

        assertThat(matching(violations(request), ALLOCATION_NOT_DECLARED)).hasSize(2);
    }

    @Test
    @DisplayName("U17.4 — доля аллокации 100: верхняя граница диапазона включена")
    void u17_4_theUpperFractionBoundIsInclusive() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(decimal("100"));

        List<String> violations = violations(request);

        assertThat(matching(violations, ALLOCATION_RANGE)).isEmpty();
        assertThat(matching(violations, HEADROOM))
                .as("запас нотинала — предмет группы U12")
                .hasSize(1);
    }

    @Test
    @DisplayName("U17.5 — доля аллокации 0: нулевая аллокация — не вход")
    void u17_5_aZeroAllocationIsOutOfRange() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(decimal("0"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(ALLOCATION_RANGE)
                .contains("доля объявления лежит в (0; 100], получено 0");
    }

    @Test
    @DisplayName("U17.6 — доля аллокации отрицательна: тот же код")
    void u17_6_aNegativeAllocationIsOutOfRangeToo() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(decimal("-5"));

        assertThat(violations(request)).singleElement().asString().contains(ALLOCATION_RANGE);
    }

    @Test
    @DisplayName("U17.7 — доля аллокации 100.01: верхняя граница строгая")
    void u17_7_theUpperBoundIsStrictAboveHundred() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setAllocationPercents(decimal("100.01"));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, ALLOCATION_RANGE)).hasSize(1);
        assertThat(matching(violations, HEADROOM)).hasSize(1);
    }

    @Test
    @DisplayName("U17.8 — доля закрытия первичной постановки 0: свой код и падение покрытия")
    void u17_8_aZeroCloseFractionCarriesItsOwnCodeAndBreaksCoverage() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setCloseFractionPercents(decimal("0"));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, FRACTION_RANGE)).hasSize(1);
        assertThat(matching(violations, COVERAGE)).hasSize(1);
    }

    @Test
    @DisplayName("U17.9 — доля закрытия 100: нарушений нет")
    void u17_9_aFullCloseFractionIsLegal() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setCloseFractionPercents(decimal("100"));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U17.10 — доля закрытия 150: диапазон и покрытие шага полного набора")
    void u17_10_anOversizedCloseFractionBreaksBothChecks() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setCloseFractionPercents(decimal("150"));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, FRACTION_RANGE)).hasSize(1);
        assertThat(matching(violations, COVERAGE)).hasSize(1);
    }

    @Test
    @DisplayName("U17.11 — доля закрытия опущена: предмет проверки — диапазон, а не наличие")
    void u17_11_anAbsentCloseFractionRaisesNoRangeCode() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").setCloseFractionPercents(null);

        List<String> violations = violations(request);

        assertThat(matching(violations, FRACTION_RANGE)).isEmpty();
        assertThat(matching(violations, COVERAGE)).hasSize(1);
    }

    @Test
    @DisplayName("U17.12 — обнулены обе доли: два диапазонных кода РАЗНЫЕ, плюс покрытие")
    void u17_12_eachZeroedFractionIsAddressedByItsOwnCode() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        entryAction(detail).setAllocationPercents(decimal("0"));
        algoAction(detail, "bull_protection_oco").setCloseFractionPercents(decimal("0"));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(3);
        assertThat(matching(violations, ALLOCATION_RANGE)).hasSize(1);
        assertThat(matching(violations, FRACTION_RANGE)).hasSize(1);
        assertThat(matching(violations, COVERAGE)).hasSize(1);
        assertThat(matching(violations, HEADROOM))
                .as("нулевая аллокация нотинала не занимает")
                .isEmpty();
    }

    @Test
    @DisplayName("U17.13 — нулевая доля у СНИМАЮЩЕГО действия: область — всякое действие заявки")
    void u17_13_theRangeCheckCoversEveryAlgoOrderAction() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_oco_cancel").setCloseFractionPercents(decimal("0"));

        assertThat(violations(request)).singleElement().asString().contains(FRACTION_RANGE);
    }

    /** Объявление с входным действием названной доли аллокации. */
    private StrategyTrancheApiModel entryTranche(String key, String allocation) {
        StrategyTrancheApiModel tranche = newTranche(key, 1, false);
        tranche.setStepsByStatus(new LinkedHashMap<>(Map.of("PRECHECK",
                List.of(newStep("ENTRY", newOrder(key + "_entry", "ENTRY", "LONG", allocation))))));
        return tranche;
    }
}
