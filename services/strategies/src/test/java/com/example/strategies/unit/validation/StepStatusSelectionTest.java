package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.dealStepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newPositionAction;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
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
 * Шаги объявляются только под статусом, где их отбирают, — группа
 * {@code U37} документа `.claude/tests/cases/strategy-definition-validation.md`
 * (дом — docs/rules/strategy-validation.md §«Что проверяется на создании»;
 * отбор по статусам — разделы «Шаги статуса» компонент-доков обработчиков
 * транша и сделки).
 *
 * <p><b>Перечни статусов с отбором у двух уровней разные:</b> у транша —
 * все статусы, кроме терминального, у сделки — только активный. Клетки
 * берут обе стороны каждого уровня: иначе они были бы зелены у валидатора,
 * запрещающего всё, кроме эталонных ключей. У сделки клетки перебирают
 * все четыре статуса без отбора — {@code EXIT_PENDING}, {@code CLOSED},
 * {@code ERROR}, {@code EMERGENCY_CLOSED} ({@code U37.2}, {@code U37.3},
 * {@code U37.8}, {@code U37.9}): правка, внёсшая любой из них в перечень
 * статусов с отбором, роняет свою клетку.
 *
 * <p>Подложенный шаг несёт только выход позиции: такой пакет законен на
 * обоих уровнях и не задевает ни покрытия, ни нотинала — единственным
 * нарушением остаётся статус.
 */
class StepStatusSelectionTest {

    private static final String WITHOUT_SELECTION = "STRATEGY_STEP_STATUS_WITHOUT_SELECTION";

    @Test
    @DisplayName("U37.1 — базовая сборка: шаги обоих уровней стоят под статусами с отбором")
    void u37_1_theReferenceDeclaresStepsOnlyUnderSelectedStatuses() {
        assertThat(matching(violations(reference()), WITHOUT_SELECTION)).isEmpty();
    }

    @Test
    @DisplayName("U37.2 — шаг сделки под координированным выходом: его не отбирает никто")
    void u37_2_aDealStepUnderExitPendingIsRejected() {
        CreateStrategyApiRequest request = reference();
        dealStepsByStatus(bull(request)).put("EXIT_PENDING", exitStep("deal_exit_pending"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].stepsByStatus[EXIT_PENDING] " + WITHOUT_SELECTION);
    }

    @Test
    @DisplayName("U37.3 — шаг сделки под терминальным статусом: тот же отказ")
    void u37_3_aDealStepUnderATerminalStatusIsRejected() {
        CreateStrategyApiRequest request = reference();
        dealStepsByStatus(bull(request)).put("CLOSED", exitStep("deal_closed"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].stepsByStatus[CLOSED] " + WITHOUT_SELECTION);
    }

    @Test
    @DisplayName("U37.4 — шаг транша под терминальным статусом: отбора нет и у транша")
    void u37_4_aTrancheStepUnderClosedIsRejected() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("CLOSED", exitStep("tranche_closed"));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[bull_main].stepsByStatus[CLOSED] " + WITHOUT_SELECTION);
    }

    @Test
    @DisplayName("U37.5 — шаг транша под нетерминальными статусами вне эталона: отбор есть, отказа нет")
    void u37_5_trancheStepsUnderEveryNonTerminalStatusAreAccepted() {
        for (String status : List.of("ENTRY_SUBMITTED", "PROTECTION_SWITCHED", "EXIT_PENDING")) {
            CreateStrategyApiRequest request = reference();
            stepsByStatus(tranche(bull(request))).put(status, exitStep("tranche_" + status.toLowerCase()));

            assertThat(violations(request)).as(status).isEmpty();
        }
    }

    @Test
    @DisplayName("U37.6 — пустой перечень под статусом без отбора: объявлено ничего, отказа нет")
    void u37_6_anEmptyStepListUnderAnUnselectedStatusDeclaresNothing() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("CLOSED", new ArrayList<>());
        dealStepsByStatus(bull(request)).put("EXIT_PENDING", new ArrayList<>());

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U37.7 — ключ вне перечня статусов: отвергает разбор перечня, второго нарушения нет")
    void u37_7_anUnknownStatusKeyIsLeftToTheEnumCheck() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("ACTIVE", exitStep("tranche_unknown"));

        List<String> violations = violations(request);

        assertThat(matching(violations, "stepsByStatus key: unknown value ACTIVE")).hasSize(1);
        assertThat(matching(violations, WITHOUT_SELECTION)).isEmpty();
    }

    /**
     * Ошибочное состояние сделки шагов уровня сделки не отбирает: обычные
     * шаги стратегии в нём не выполняются, а единственное ребро из него ведёт
     * в аварийный терминал — ребра в выход, которое такой шаг делает, у
     * {@code ERROR} нет (docs/rules/strategy-validation.md §«Что проверяется
     * на создании»; docs/lifecycles/Deal.md).
     */
    @Test
    @DisplayName("U37.8 — шаг сделки под ошибочным состоянием: ребра в выход у него нет, отбора тоже")
    void u37_8_aDealStepUnderErrorIsRejected() {
        CreateStrategyApiRequest request = reference();
        dealStepsByStatus(bull(request)).put("ERROR", exitStep("deal_error"));

        assertThat(violations(request))
                .as("U37.8: шаг сделки под ERROR")
                .singleElement()
                .asString()
                .contains("details[0].stepsByStatus[ERROR] " + WITHOUT_SELECTION);
    }

    @Test
    @DisplayName("U37.9 — шаг сделки под аварийным терминалом: терминальный статус не отбирает никто")
    void u37_9_aDealStepUnderEmergencyClosedIsRejected() {
        CreateStrategyApiRequest request = reference();
        dealStepsByStatus(bull(request)).put("EMERGENCY_CLOSED", exitStep("deal_emergency_closed"));

        assertThat(violations(request))
                .as("U37.9: шаг сделки под EMERGENCY_CLOSED")
                .singleElement()
                .asString()
                .contains("details[0].stepsByStatus[EMERGENCY_CLOSED] " + WITHOUT_SELECTION);
    }

    /** Шаг выхода с единственным действием выхода позиции — законный пакет обоих уровней. */
    private List<StrategyStepApiModel> exitStep(String actionKey) {
        return new ArrayList<>(List.of(newStep("EXIT", newPositionAction(actionKey))));
    }
}
