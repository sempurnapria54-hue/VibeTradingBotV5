package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.decimal;
import static com.example.strategies.unit.validation.ValidationFixture.entryAction;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyOrderActionApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Безубыток допустим только как перенос уровня — группа {@code U18}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/spec/stop-distance.json, величина {@code breakevenRoleAllowed};
 * форма на дереве — docs/spec/strategy-reference.json, величина
 * {@code breakevenAsPrimaryStop}).
 *
 * <p><b>Способ расчёта читается с ДВУХ носителей уровня</b> — своих
 * настроек стопа и настроек встроенной защиты входа, — иначе тот же
 * безубыток проходил бы второй тропой.
 *
 * <p><b>Перенос требует НАЗВАННОЙ цели, а резолв — непустой.</b>
 * Бланковая строка проходит охрану резолва и не проходит предикат
 * переноса, и клетка называет оба нарушения: они разводятся только этим
 * различием.
 */
class BreakevenTransferTest {

    private static final String NOT_A_TRANSFER = "STRATEGY_BREAKEVEN_NOT_A_TRANSFER";

    private static final String COVERAGE = "STRATEGY_PROTECTION_COVERAGE_INCOMPLETE";

    @Test
    @DisplayName("U18.1 — базовая сборка: безубыток объявлен защитным замещением с целью")
    void u18_1_theReferenceDeclaresBreakevenAsATransfer() {
        assertThat(matching(violations(reference()), NOT_A_TRANSFER)).isEmpty();
    }

    @Test
    @DisplayName("U18.2 — цель переноса удалена: замещение стало первичной постановкой")
    void u18_2_aBreakevenWithoutATargetIsAPrimaryStop() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setTargetActionKey(null);

        List<String> violations = violations(request);

        assertThat(violations).singleElement().asString().contains(NOT_A_TRANSFER);
        assertThat(matching(violations, COVERAGE))
                .as("неназванная цель читается как всё покрытие, а замещение объявляет сто")
                .isEmpty();
    }

    @Test
    @DisplayName("U18.3 — способ первичной защиты сменён на безубыток: первичной он быть не может")
    void u18_3_breakevenMayNotBeThePrimaryProtection() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").getStopLossSettings().setCalculationType("BREAKEVEN");

        assertThat(violations(request)).singleElement().asString().contains(NOT_A_TRANSFER);
    }

    @Test
    @DisplayName("U18.4 — способ ВСТРОЕННОЙ защиты входа сменён на безубыток: носителей уровня два")
    void u18_4_theAttachedProtectionCarriesTheSameCalculationType() {
        CreateStrategyApiRequest request = reference();
        entryAction(bull(request)).getAttachedProtection().getStopLossSettings().setCalculationType("BREAKEVEN");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("details[0].tranches[bull_main].stepsByStatus[PRECHECK][0].actions[0] " + NOT_A_TRANSFER);
    }

    @Test
    @DisplayName("U18.5 — цель переноса — строка из пробелов: перенос и резолв разводятся небланковостью")
    void u18_5_aBlankTargetPassesTheResolutionGuardAndFailsTheTransferPredicate() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").setTargetActionKey("   ");

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, NOT_A_TRANSFER)).hasSize(1);
        assertThat(matching(violations, "references unknown action key")).hasSize(1);
    }

    @Test
    @DisplayName("U18.6 — безубыток у действия-ЗАЯВКИ с названной целью: перенос — свойство условной")
    void u18_6_anOrderActionIsNeverALevelTransfer() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        StrategyOrderActionApiModel entry = entryAction(detail);
        entry.getAttachedProtection().getStopLossSettings().setCalculationType("BREAKEVEN");
        entry.setTargetActionKey("bull_protection_oco");

        assertThat(violations(request)).singleElement().asString().contains(NOT_A_TRANSFER);
    }

    @Test
    @DisplayName("U18.7 — безубыток у СОЗДАЮЩЕГО действия с целью: перенос требует замещения")
    void u18_7_aCreatingActionWithATargetIsStillNotATransfer() {
        CreateStrategyApiRequest request = reference();
        StrategyDetailApiModel detail = bull(request);
        algoAction(detail, "bull_protection_oco").getStopLossSettings().setCalculationType("BREAKEVEN");
        algoAction(detail, "bull_protection_oco").setTargetActionKey("bull_entry");

        assertThat(violations(request)).singleElement().asString().contains(NOT_A_TRANSFER);
    }

    @Test
    @DisplayName("U18.8 — процент от цены входа в первичной роли: роль ограничена только у безубытка")
    void u18_8_otherCalculationTypesCarryNoRoleRestriction() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_protection_oco").getStopLossSettings()
                .setCalculationType("ENTRY_PRICE_PERCENT");

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @Tag("debt")
    @DisplayName("U18.9 — безубыток объявлен вместе с долей дистанции: дом требует отказа (F1)")
    void u18_9_breakevenDeclaringADistanceIsRejectedByTheHome() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").getStopLossSettings()
                .setDistancePercents(decimal("100"));

        assertThat(matching(violations(request), "distancePercents"))
                .as("ожидание из дома: у безубытка доля дистанции не объявляется")
                .isNotEmpty();
    }
}
