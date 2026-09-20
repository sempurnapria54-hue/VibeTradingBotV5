package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.actions;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.entryStep;
import static com.example.strategies.unit.validation.ValidationFixture.exitStep;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
import static com.example.strategies.unit.validation.ValidationFixture.protectionStep;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.stepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Пустой пакет действий законен только у шага выхода — группа
 * {@code U15} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/no-partial-close.md §«Две законные формы полного выхода»;
 * реджект — docs/rules/strategy-validation.md §«Что проверяется на
 * создании»).
 *
 * <p><b>Экземпция названа ОДНОМУ типу, а не классу «нештатные».</b> Шаг
 * страховки под неё не попадает, и неизвестный тип — тоже: сверка идёт
 * с именем типа выхода.
 *
 * <p><b>Опустошённый пакет уносит КЛЮЧИ своих действий</b>, поэтому
 * клетка называет и битые ссылки, которые после этого появляются: набор
 * ключей детали собирается по наличным действиям.
 */
class StepPackageTest {

    private static final String EMPTY_PACKAGE = "STRATEGY_STEP_ACTIONS_EMPTY";

    @Test
    @DisplayName("U15.1 — пакет агрегатного шага выхода опустошён: он вправе нести только условие")
    void u15_1_anExitStepMayCarryOnlyItsCondition() {
        CreateStrategyApiRequest request = reference();
        exitStep(bull(request)).setActions(new ArrayList<>());

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U15.2 — базовая сборка: у каждого шага непустой пакет действий")
    void u15_2_everyReferenceStepCarriesActions() {
        assertThat(matching(violations(reference()), EMPTY_PACKAGE)).isEmpty();
    }

    @Test
    @DisplayName("U15.3 — пакет шага первичной защиты пуст: четыре нарушения, и две из них — ссылки")
    void u15_3_anEmptyProtectionPackageAlsoBreaksTheReferencesToIt() {
        CreateStrategyApiRequest request = reference();
        protectionStep(bull(request)).setActions(new ArrayList<>());

        List<String> violations = violations(request);

        assertThat(violations).hasSize(4);
        assertThat(matching(violations, EMPTY_PACKAGE)).hasSize(1);
        assertThat(matching(violations, "STRATEGY_PROTECTION_COVERAGE_INCOMPLETE"))
                .as("защитных действий в шаге полного набора больше нет")
                .hasSize(1);
        assertThat(matching(violations, "references unknown action key bull_protection_oco"))
                .as("замещение и снятие называют целью ключ удалённого действия")
                .hasSize(2);
    }

    @Test
    @DisplayName("U15.4 — поле действий шага входа опущено вовсе: пустой и отсутствующий не разведены")
    void u15_4_anAbsentActionListIsIndistinguishableFromAnEmptyOne() {
        CreateStrategyApiRequest request = reference();
        entryStep(bull(request)).setActions(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[bull_main].stepsByStatus[PRECHECK][0].actions " + EMPTY_PACKAGE);
    }

    @Test
    @DisplayName("U15.5 — пакет шага страховки пуст: экземпция названа ОДНОМУ типу")
    void u15_5_theFailSafeStepIsNotExempt() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("PROTECTION_SWITCHED", List.of(bareStep("FAIL_SAFE")));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(EMPTY_PACKAGE);
    }

    @Test
    @DisplayName("U15.6 — шаг выхода с пустым пакетом на ТРАНШЕ: экземпция читается по типу шага")
    void u15_6_theExemptionIsReadFromTheStepTypeNotTheLevel() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("PROTECTION_SWITCHED", List.of(bareStep("EXIT")));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U15.7 — тип шага — неизвестная строка при пустом пакете: два нарушения")
    void u15_7_anUnknownStepTypeIsNotExemptEither() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("PROTECTION_SWITCHED", List.of(bareStep("TEARDOWN")));

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, "stepType: unknown value TEARDOWN")).hasSize(1);
        assertThat(matching(violations, EMPTY_PACKAGE)).hasSize(1);
    }

    /** Шаг без единого действия — вход клеток группы. */
    private StrategyStepApiModel bareStep(String stepType) {
        StrategyStepApiModel step = newStep(stepType);
        actions(step).clear();
        return step;
    }
}
