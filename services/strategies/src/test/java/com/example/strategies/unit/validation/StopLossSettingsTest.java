package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.algoAction;
import static com.example.strategies.unit.validation.ValidationFixture.bull;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StopLossSettingsApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Настройки стопа: способ, его ссылки и доля дистанции — группа
 * {@code U21} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/spec/stop-distance.json, величины {@code calculationTypeKnown} и
 * {@code distanceDeclaredWhenNeeded}).
 *
 * <p><b>Ссылка требуется ПО СПОСОБУ расчёта</b>, и неразобранный способ
 * выключает обе: кейс, называющий только ссылку, был бы зелен у
 * валидатора, требующего её всегда.
 *
 * <p><b>Тип индикатора у этой ссылки не сверяется</b> — в отличие от той
 * же ссылки у настройки структуры, — и ожидание взято по коду: дом типа
 * для неё не называет (пробел {@code G4} документа).
 */
class StopLossSettingsTest {

    @Test
    @DisplayName("U21.1 — базовая сборка: способ объявлен, ссылка резолвится")
    void u21_1_theReferenceStopSettingsAreWellFormed() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U21.2 — способ расчёта — неизвестная строка: ссылки по способу не проверяются")
    void u21_2_anUnknownCalculationTypeSilencesBothReferences() {
        CreateStrategyApiRequest request = reference();
        stopSettings(request).setCalculationType("FIBONACCI");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".stopLossSettings.calculationType: unknown value FIBONACCI");
    }

    @Test
    @DisplayName("U21.3 — способ от индикатора, ссылка опущена: она обязательна")
    void u21_3_anIndicatorBasedStopRequiresItsReference() {
        CreateStrategyApiRequest request = reference();
        stopSettings(request).setIndicatorKey(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".stopLossSettings.indicatorKey is required and must reference a indicator setting");
    }

    @Test
    @DisplayName("U21.4 — ссылка на индикатор названа несуществующим ключом")
    void u21_4_anUnknownIndicatorKeyDoesNotResolve() {
        CreateStrategyApiRequest request = reference();
        stopSettings(request).setIndicatorKey("no_such_indicator");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("references unknown indicator setting key no_such_indicator");
    }

    @Test
    @DisplayName("U21.5 — способ от структурного уровня, ссылка на структуру опущена")
    void u21_5_aStructureBasedStopRequiresItsReference() {
        CreateStrategyApiRequest request = reference();
        StopLossSettingsApiModel settings = stopSettings(request);
        settings.setCalculationType("MARKET_STRUCTURE_BUFFER_PERCENT");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".stopLossSettings.structureKey is required and must reference a market structure setting");
    }

    @Test
    @DisplayName("U21.6 — ссылка на структуру названа несуществующим ключом")
    void u21_6_anUnknownStructureKeyDoesNotResolve() {
        CreateStrategyApiRequest request = reference();
        StopLossSettingsApiModel settings = stopSettings(request);
        settings.setCalculationType("MARKET_STRUCTURE_BUFFER_PERCENT");
        settings.setStructureKey("no_such_structure");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("references unknown market structure setting key no_such_structure");
    }

    @Test
    @DisplayName("U21.7 — ссылка резолвится в индикатор ДРУГОГО типа: тип здесь не сверяется")
    void u21_7_theReferencedIndicatorTypeIsNotChecked() {
        CreateStrategyApiRequest request = reference();
        stopSettings(request).setIndicatorKey("ema_fast_15m");

        assertThat(violations(request))
                .as("пробел G4: дом типа для этой ссылки не называет")
                .isEmpty();
    }

    @Test
    @Tag("debt")
    @DisplayName("U21.8 — безубыток вместе с долей дистанции: дом требует отказа (F1)")
    void u21_8_breakevenWithADistanceIsRejectedByTheHome() {
        CreateStrategyApiRequest request = reference();
        algoAction(bull(request), "bull_sl_to_breakeven").getStopLossSettings()
                .setDistancePercents(ValidationFixture.decimal("100"));

        assertThat(violations(request))
                .as("ожидание из дома: у безубытка доля дистанции не объявляется")
                .isNotEmpty();
    }

    @Test
    @Tag("debt")
    @DisplayName("U21.9 — способ от индикатора без доли дистанции: дом требует отказа (F1)")
    void u21_9_anIndicatorBasedStopWithoutADistanceIsRejectedByTheHome() {
        CreateStrategyApiRequest request = reference();
        stopSettings(request).setDistancePercents(null);

        assertThat(violations(request))
                .as("ожидание из дома: иначе отказ приходит в рантайме на расчёте цены уровня")
                .isNotEmpty();
    }

    @Test
    @DisplayName("U21.10 — база триггера в настройках стопа опущена: сверка безусловна")
    void u21_10_theTriggerPriceTypeIsCheckedUnconditionally() {
        CreateStrategyApiRequest request = reference();
        stopSettings(request).setTriggerPriceType(null);

        List<String> violations = violations(request);

        assertThat(violations)
                .singleElement()
                .asString()
                .contains(".stopLossSettings.triggerPriceType: unknown value null");
    }

    /** Настройки стопа первичной защиты — предмет группы. */
    private StopLossSettingsApiModel stopSettings(CreateStrategyApiRequest request) {
        return algoAction(bull(request), "bull_protection_oco").getStopLossSettings();
    }
}
