package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.action;
import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bear;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ключи действий и резолв цели — группа {@code U16} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/models/domain/aggregate/Strategy.md §«Ключи и ссылки»).
 *
 * <p><b>Область уникальности и область резолва — ДЕТАЛЬ, оба уровня
 * объявления.</b> Ключ, повторённый в другой детали, законен; цель,
 * названная в другой детали, не резолвится. Кейс, берущий один уровень,
 * был бы зелен у валидатора, собирающего ключи только по траншам.
 */
class ActionKeysAndTargetTest {

    private static final String DUPLICATE = "duplicate action key";

    private static final String UNKNOWN_TARGET = "references unknown action key";

    @Test
    @DisplayName("U16.1 — базовая сборка: ключи уникальны, цели резолвятся")
    void u16_1_theReferenceKeysAreUniqueAndItsTargetsResolve() {
        List<String> violations = violations(reference());

        assertThat(matching(violations, DUPLICATE)).isEmpty();
        assertThat(matching(violations, UNKNOWN_TARGET)).isEmpty();
    }

    @Test
    @DisplayName("U16.2 — два действия одного шага несут один ключ: дубль с названным ключом")
    void u16_2_twoActionsOfOneStepMayNotShareAKey() {
        CreateStrategyApiRequest request = reference();
        action(bull(request), "bull_trailing").setKey("bull_oco_cancel");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0]: " + DUPLICATE + " bull_oco_cancel");
    }

    @Test
    @DisplayName("U16.3 — ключ шага транша повторён у агрегатного шага: область — вся деталь")
    void u16_3_theUniquenessScopeSpansBothDeclarationLevels() {
        CreateStrategyApiRequest request = reference();
        action(bull(request), "bull_exit").setKey("bull_entry");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(DUPLICATE + " bull_entry");
    }

    @Test
    @DisplayName("U16.4 — тот же ключ у действия ДРУГОЙ детали: область — деталь, а не стратегия")
    void u16_4_theUniquenessScopeStopsAtTheDetailBoundary() {
        CreateStrategyApiRequest request = reference();
        action(bear(request), "bear_entry").setKey("bull_entry");

        assertThat(matching(violations(request), DUPLICATE)).isEmpty();
    }

    @Test
    @DisplayName("U16.5 — цель замещения названа несуществующим ключом: ссылка обязана остаться внутри")
    void u16_5_anUnknownTargetKeyIsRejected() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setTargetActionKey("no_such_action");

        List<String> violations = violations(request);

        assertThat(matching(violations, UNKNOWN_TARGET + " no_such_action")).hasSize(1);
        assertThat(matching(violations, "STRATEGY_PROTECTION_COVERAGE_INCOMPLETE")).isEmpty();
    }

    @Test
    @DisplayName("U16.6 — цель названа ключом действия ДРУГОЙ детали: набор ключей на деталь")
    void u16_6_aTargetInAnotherDetailDoesNotResolve() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setTargetActionKey("bear_protection_oco");

        assertThat(matching(violations(request), UNKNOWN_TARGET + " bear_protection_oco")).hasSize(1);
    }

    @Test
    @DisplayName("U16.7 — цель у создающего действия не названа: она обязательна не у всех видов")
    void u16_7_aCreatingActionNeedsNoTarget() {
        assertThat(matching(violations(reference()), UNKNOWN_TARGET)).isEmpty();
    }

    @Test
    @DisplayName("U16.8 — ключ действия опущен: в набор ключей пустое не попадает")
    void u16_8_anAbsentActionKeyEntersNoKeySet() {
        CreateStrategyApiRequest request = reference();
        action(bull(request), "bull_trailing").setKey(null);

        assertThat(violations(request))
                .as("достижимость исключена непустотой ключа у поверхности")
                .isEmpty();
    }

    @Test
    @DisplayName("U16.9 — цель названа ключом действия того же шага: порядок объявления не важен")
    void u16_9_aTargetInTheSameStepResolves() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_trailing").setTargetActionKey("bull_oco_cancel");

        assertThat(violations(request)).isEmpty();
    }
}
