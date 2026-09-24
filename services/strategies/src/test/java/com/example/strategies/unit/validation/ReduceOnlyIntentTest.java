package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.bear;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Намерение reduce-only объявлено у всякого действия-заявки — группа
 * {@code U33} документа `.claude/tests/cases/strategy-definition-validation.md`
 * (дом — docs/rules/strategy-validation.md §«Что проверяется на создании»).
 *
 * <p><b>Предмет проверки — наличие, а не значение.</b> Утверждённое
 * намерение у входа кода не поднимает: иначе кейс был бы зелен у
 * валидатора, спутавшего обязательность с запретом.
 */
class ReduceOnlyIntentTest {

    private static final String NOT_DECLARED = "STRATEGY_ACTION_REDUCE_ONLY_NOT_DECLARED";

    @Test
    @DisplayName("U33.1 — базовая сборка: нарушений о намерении нет")
    void u33_1_theReferenceDeclaresTheIntent() {
        assertThat(matching(violations(reference()), NOT_DECLARED)).isEmpty();
    }

    @Test
    @DisplayName("U33.2 — намерение у входа снято: ровно одно нарушение с путём поля")
    void u33_2_anAbsentIntentIsRejected() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPositionReducingOnly(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(NOT_DECLARED)
                .contains(".positionReducingOnly");
    }

    @Test
    @DisplayName("U33.3 — намерение у входа утверждено: код обязательности не поднимается")
    void u33_3_aDeclaredTrueIntentIsNotAnAbsence() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPositionReducingOnly(Boolean.TRUE);

        assertThat(matching(violations(request), NOT_DECLARED)).isEmpty();
    }

    @Test
    @DisplayName("U33.4 — намерение снято у входов обеих деталей: два нарушения")
    void u33_4_everyOrderActionReportsItsOwnAbsentIntent() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).setPositionReducingOnly(null);
        entryAction(bear(request)).setPositionReducingOnly(null);

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, NOT_DECLARED)).hasSize(2);
    }
}
