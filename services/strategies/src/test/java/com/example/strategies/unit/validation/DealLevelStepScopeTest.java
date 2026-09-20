package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.dealStepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.exitStep;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newAlgo;
import static com.example.strategies.unit.validation.ValidationFixture.newPositionAction;
import static com.example.strategies.unit.validation.ValidationFixture.newStep;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.stepsByStatus;
import static com.example.strategies.unit.validation.ValidationFixture.tranche;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Агрегатная поверхность шагов детали — группа {@code U14} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/models/domain/aggregate/Strategy.md §StrategyDetail: на детали
 * законны только шаги выхода и страховки).
 *
 * <p><b>Узость — свойство УРОВНЯ объявления, а не типа шага.</b> Тот же
 * тип на транше законен, и клетка предъявляет обе стороны: иначе она
 * была бы зелена у валидатора, запрещающего тип везде.
 *
 * <p><b>Ключи двух карт читаются РАЗНЫМИ статусными моделями</b> — карта
 * объявления статусом транша, карта детали статусом сделки, — и
 * пересечение у них из двух значений, поэтому кейс берёт значение вне
 * пересечения намеренно.
 */
class DealLevelStepScopeTest {

    private static final String OUT_OF_SCOPE = "STRATEGY_DEAL_LEVEL_STEP_OUT_OF_SCOPE";

    @Test
    @DisplayName("U14.1 — базовая сборка: на уровне сделки объявлен только шаг выхода")
    void u14_1_theReferenceDeclaresOnlyAnExitStepAtTheDealLevel() {
        assertThat(matching(violations(reference()), OUT_OF_SCOPE)).isEmpty();
    }

    @Test
    @DisplayName("U14.2 — шаг страховки на уровне сделки: второй допустимый тип")
    void u14_2_theFailSafeStepIsTheSecondAllowedType() {
        CreateStrategyApiRequest request = reference();
        dealStepsByStatus(bull(request)).put("EXIT_PENDING",
                List.of(newStep("FAIL_SAFE", newPositionAction("deal_fail_safe"))));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U14.3 — шаг первичной защиты на уровне сделки: выход за поверхность")
    void u14_3_aMainProtectionStepIsOutOfTheDealLevelScope() {
        CreateStrategyApiRequest request = reference();
        exitStep(bull(request)).setStepType("MAIN_PROTECTION");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].stepsByStatus[ACTIVE][0].stepType " + OUT_OF_SCOPE)
                .contains("объявлено MAIN_PROTECTION");
    }

    @Test
    @DisplayName("U14.4 — шаг входа на уровне сделки: тот же отказ со своим типом")
    void u14_4_anEntryStepIsOutOfTheDealLevelScopeToo() {
        CreateStrategyApiRequest request = reference();
        exitStep(bull(request)).setStepType("ENTRY");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(OUT_OF_SCOPE)
                .contains("объявлено ENTRY");
    }

    @Test
    @DisplayName("U14.5 — тип агрегатного шага опущен: пустой тип двум допустимым не принадлежит")
    void u14_5_anAbsentStepTypeBreaksBothChecks() {
        CreateStrategyApiRequest request = reference();
        exitStep(bull(request)).setStepType(null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, "details[0].stepsByStatus[ACTIVE][0].stepType: unknown value null"))
                .hasSize(1);
        assertThat(matching(violations, OUT_OF_SCOPE)).hasSize(1);
    }

    @Test
    @DisplayName("U14.6 — тот же тип шага на ТРАНШЕ: узость — свойство уровня, а не типа")
    void u14_6_theSameStepTypeIsLegalOnATranche() {
        CreateStrategyApiRequest request = reference();
        stepsByStatus(tranche(bull(request))).put("PROTECTION_SWITCHED",
                List.of(newStep("MAIN_PROTECTION", fullProtection("switched_protection"))));

        assertThat(matching(violations(request), OUT_OF_SCOPE)).isEmpty();
    }

    @Test
    @DisplayName("U14.7 — ключ карты агрегатных шагов не статус сделки: сами шаги проверяются")
    void u14_7_anUnknownDealStatusKeyDoesNotStopTheStepTraversal() {
        CreateStrategyApiRequest request = reference();
        StrategyStepApiModel step = exitStep(bull(request));
        step.setStepType("MAIN_PROTECTION");
        dealStepsByStatus(bull(request)).remove("ACTIVE");
        dealStepsByStatus(bull(request)).put("MANAGING", List.of(step));

        List<String> violations = violations(request);

        assertThat(matching(violations, "details[0].stepsByStatus key: unknown value MANAGING")).hasSize(1);
        assertThat(matching(violations, OUT_OF_SCOPE))
                .as("шаг под неизвестным ключом проверяется как обычно")
                .hasSize(1);
    }

    @Test
    @DisplayName("U14.8 — ключ карты шагов транша — статус СДЕЛКИ вне пересечения моделей")
    void u14_8_aDealStatusKeyOnATrancheMapIsUnknown() {
        CreateStrategyApiRequest request = reference();
        Map<String, List<StrategyStepApiModel>> byStatus = stepsByStatus(tranche(bull(request)));
        byStatus.put("ACTIVE", byStatus.remove("MANAGING"));

        assertThat(matching(violations(request),
                "details[0].tranches[bull_main].stepsByStatus key: unknown value ACTIVE"))
                .as("пересечение статусных моделей — два значения, и оно взято вне его")
                .hasSize(1);
    }

    /** Защитное создание полного покрытия — шаг первичной защиты его требует. */
    private StrategyAlgoOrderActionApiModel fullProtection(String key) {
        StrategyAlgoOrderActionApiModel action = newAlgo(key, "CREATE_ACTION", "STOP_LOSS");
        action.setCloseFractionPercents(decimal("100"));
        return action;
    }
}
