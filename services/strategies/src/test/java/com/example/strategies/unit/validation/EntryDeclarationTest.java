package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newOrder;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
import static com.example.strategies.unit.validation.ValidationFixture.newTranche;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.steps;
import static com.example.strategies.unit.validation.ValidationFixture.stepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.tranches;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Входное объявление: ровно одно у транша, хотя бы одно у детали —
 * группа {@code U6} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-validation.md §«Что проверяется на создании»;
 * звено — {@code StrategyDefinitionValidator#entryStepCount}).
 *
 * <p><b>Требований два, и адресуют они разные уровни.</b> «Ровно одно» —
 * свойство ОБЪЯВЛЕНИЯ, и нарушается оно у каждого объявления своей
 * строкой; «хотя бы одно» — свойство ДЕТАЛИ, и молчит, пока вход несёт
 * хоть один транш. Кейс, называющий только одно из них, зелен у
 * валидатора, потерявшего второе.
 */
class EntryDeclarationTest {

    private static final String NOT_UNIQUE = "STRATEGY_TRANCHE_ENTRY_NOT_UNIQUE";

    private static final String MISSING = "STRATEGY_ENTRY_DECLARATION_MISSING";

    @Test
    @DisplayName("U6.1 — базовая сборка: у объявления один входной шаг предвходового статуса")
    void u6_1_theReferenceDeclaresExactlyOneEntryPerTranche() {
        List<String> violations = violations(reference());

        assertThat(matching(violations, NOT_UNIQUE)).isEmpty();
        assertThat(matching(violations, MISSING)).isEmpty();
    }

    @Test
    @DisplayName("U6.2 — два шага входа в предвходовом статусе: объявление не уникально")
    void u6_2_twoEntryStepsInOneTrancheAreRejected() {
        CreateStrategyApiRequest request = reference();
        steps(tranche(bull(request)), "PRECHECK").add(entryStepNamed("extra_entry", "ENTRY"));

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_UNIQUE))
                .singleElement()
                .asString()
                .contains("details[0].tranches[0]")
                .contains("объявлено 2");
        assertThat(matching(violations, MISSING)).isEmpty();
    }

    @Test
    @DisplayName("U6.3 — шаг входа и шаг входа сеткой: в отбор входят ОБА типа")
    void u6_3_bothEntryStepTypesAreCounted() {
        CreateStrategyApiRequest request = reference();
        steps(tranche(bull(request)), "PRECHECK").add(entryStepNamed("extra_grid", "GRID_ENTRY"));

        assertThat(matching(violations(request), NOT_UNIQUE))
                .singleElement()
                .asString()
                .contains("объявлено 2");
    }

    @Test
    @DisplayName("U6.4 — входной шаг перенесён в статус сопровождения: у детали входа нет")
    void u6_4_anEntryStepOutsideThePrecheckStatusDoesNotCount() {
        CreateStrategyApiRequest request = reference();
        StrategyTrancheApiModel tranche = tranche(bull(request));
        StrategyStepApiModel entry = steps(tranche, "PRECHECK").remove(0);
        steps(tranche, "MANAGING").add(entry);

        List<String> violations = violations(request);

        assertThat(matching(violations, MISSING))
                .singleElement()
                .asString()
                .contains("details[0].tranches");
        assertThat(matching(violations, NOT_UNIQUE)).isEmpty();
    }

    @Test
    @DisplayName("U6.5 — предвходовый статус удалён вовсе: у детали входа нет")
    void u6_5_aRemovedPrecheckStatusLeavesTheDetailWithoutAnEntry() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).remove("PRECHECK");

        assertThat(matching(violations(request), MISSING)).hasSize(1);
    }

    @Test
    @DisplayName("U6.6 — два объявления, вход у одного: требование детали выполнено")
    void u6_6_oneEntryAmongTwoTranchesSatisfiesTheDetail() {
        CreateStrategyApiRequest request = reference();
        tranches(bull(request)).add(newTranche("bull_second", 1, false));

        List<String> violations = violations(request);

        assertThat(matching(violations, MISSING)).isEmpty();
        assertThat(matching(violations, NOT_UNIQUE)).isEmpty();
    }

    @Test
    @DisplayName("U6.7 — карты шагов у объявления нет вовсе: шаги не обходятся")
    void u6_7_aTrancheWithoutAStepMapIsNotTraversed() {
        CreateStrategyApiRequest request = reference();
        tranche(bull(request)).setStepsByStatus(null);

        List<String> violations = violations(request);

        assertThat(violations).singleElement().asString().contains(MISSING);
        assertThat(matching(violations, "STRATEGY_STEP_ACTIONS_EMPTY")).isEmpty();
    }

    @Test
    @DisplayName("U6.8 — предвходовый статус объявлен пустым списком: тот же исход")
    void u6_8_anEmptyPrecheckListIsIndistinguishableFromAnAbsentOne() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("PRECHECK", new ArrayList<>());

        assertThat(matching(violations(request), MISSING)).hasSize(1);
    }

    @Test
    @DisplayName("U6.9 — оба объявления детали несут по два входа: по нарушению на объявление")
    void u6_9_everyTrancheReportsItsOwnNonUniqueEntry() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        steps(tranche(detail), "PRECHECK").add(entryStepNamed("extra_entry", "ENTRY"));

        StrategyTrancheApiModel second = newTranche("bull_second", 1, false);
        second.setStepsByStatus(new LinkedHashMap<>(Map.of("PRECHECK",
                List.of(entryStepNamed("second_entry", "ENTRY"),
                        entryStepNamed("second_grid", "GRID_ENTRY")))));
        tranches(detail).add(second);

        List<String> violations = violations(request);

        assertThat(matching(violations, NOT_UNIQUE)).hasSize(2);
        assertThat(matching(violations, "details[0].tranches[0] " + NOT_UNIQUE)).hasSize(1);
        assertThat(matching(violations, "details[0].tranches[1] " + NOT_UNIQUE)).hasSize(1);
        assertThat(matching(violations, MISSING)).isEmpty();
    }

    /** Входной шаг с долей, не двигающей объявленный нотинал детали. */
    private StrategyStepApiModel entryStepNamed(String actionKey, String stepType) {
        return newStep(stepType, newOrder(actionKey, "ENTRY", "LONG", "1"));
    }
}
