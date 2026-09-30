package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newStructure;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.structure;
import static com.example.strategies.unit.validation.ValidationFixture.structures;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.StrategyMarketStructureSettingApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Настройки структуры рынка и типизованные ссылки — группа {@code U25}
 * документа `.claude/tests/cases/strategy-definition-validation.md` (дом
 * — docs/rules/strategy-condition-contract.md §«Настройка индикатора»;
 * типизация ссылок — звено {@code validateIndicatorKeyOfType}; окно
 * расчёта — docs/rules/strategy-validation.md §«Что проверяется на создании»).
 *
 * <p><b>Типизация объявлена КАЖДОЙ ссылке отдельно</b>, а не одна на
 * настройку: ссылка на индикатор эффективности требует своего типа,
 * ссылка на волатильность — своего. Кейс на одну ссылку был бы зелен у
 * разбора, сверяющего обе с одним типом.
 *
 * <p>Ожидание типизации взято ПО КОДУ: дом такого требования не
 * объявляет ни одной строкой (пробел {@code G6} документа).
 */
class StructureSettingsTest {

    private static final String REFERENCE_KEY = "phase_structure_1h";

    @Test
    @DisplayName("U25.1 — базовая сборка: одна настройка структуры без ссылок на индикаторы")
    void u25_1_theReferenceStructureSettingCarriesNoIndicatorReferences() {
        CreateStrategyApiRequest request = reference();
        StrategyMarketStructureSettingApiModel setting = structure(request, REFERENCE_KEY);

        assertThat(setting.getEfficiencyRatioKey()).isNull();
        assertThat(setting.getAtrKey()).isNull();
        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U25.2 — вторая настройка несёт ключ первой: дубль ключа настройки структуры")
    void u25_2_aReusedStructureKeyIsRejected() {
        CreateStrategyApiRequest request = reference();
        structures(request).add(newStructure(REFERENCE_KEY));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("duplicate market structure setting key " + REFERENCE_KEY);
    }

    @Test
    @DisplayName("U25.3 — таймфрейм и назначение неизвестны: два нарушения с разными путями")
    void u25_3_bothEnumFieldsAreCheckedSeparately() {
        CreateStrategyApiRequest request = reference();
        StrategyMarketStructureSettingApiModel setting = structure(request, REFERENCE_KEY);
        setting.setTimeframe("TEN_MINUTES");
        setting.setDestiny("SIGNAL");

        List<String> violations = violations(request);

        assertThat(violations).hasSize(2);
        assertThat(matching(violations, ".timeframe: unknown value TEN_MINUTES")).hasSize(1);
        assertThat(matching(violations, ".destiny: unknown value SIGNAL")).hasSize(1);
    }

    @Test
    @DisplayName("U25.4 — ссылка на индикатор эффективности не резолвится")
    void u25_4_anUnknownEfficiencyRatioKeyDoesNotResolve() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).setEfficiencyRatioKey("no_such_indicator");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".efficiencyRatioKey references unknown indicator setting key no_such_indicator");
    }

    @Test
    @DisplayName("U25.5 — ссылка на индикатор эффективности резолвится в ЧУЖОЙ тип")
    void u25_5_theEfficiencyRatioReferenceIsTyped() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).setEfficiencyRatioKey("ema_fast_15m");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("must reference an indicator of type EFFICIENCY_RATIO, but ema_fast_15m is EMA");
    }

    @Test
    @DisplayName("U25.6 — ссылка на индикатор волатильности резолвится в скользящую среднюю")
    void u25_6_theAtrReferenceCarriesItsOwnExpectedType() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).setAtrKey("ema_fast_15m");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("must reference an indicator of type ATR, but ema_fast_15m is EMA");
    }

    @Test
    @DisplayName("U25.7 — обе ссылки опущены: проверяются только объявленные")
    void u25_7_absentReferencesAreNotChecked() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U25.8 — срок годности настройки структуры не разбирается")
    void u25_8_anUnparsableStructureDurationIsRejected() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).setExpirationDuration("два часа");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".expirationDuration: invalid ISO-8601 duration два часа");
    }

    @Test
    @DisplayName("U25.9 — окно расчёта структуры опущено: нарушение обязательности, диапазона нет")
    void u25_9_anUndeclaredLookbackWindowIsRejected() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).getParams().setLookbackBars(null);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".params.lookbackBars STRATEGY_STRUCTURE_LOOKBACK_NOT_DECLARED");
    }

    @Test
    @DisplayName("U25.10 — окно расчёта структуры нулевое: нарушение диапазона, обязательность молчит")
    void u25_10_aZeroLookbackWindowIsRejected() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).getParams().setLookbackBars(0);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".params.lookbackBars STRATEGY_STRUCTURE_LOOKBACK_NOT_POSITIVE")
                .contains("получено 0");
    }

    @Test
    @DisplayName("U25.11 — окно расчёта структуры отрицательное: то же нарушение диапазона")
    void u25_11_aNegativeLookbackWindowIsRejected() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).getParams().setLookbackBars(-5);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".params.lookbackBars STRATEGY_STRUCTURE_LOOKBACK_NOT_POSITIVE")
                .contains("получено -5");
    }

    @Test
    @DisplayName("U25.12 — окно в один бар: нижняя граница диапазона включена")
    void u25_12_aOneBarLookbackWindowPasses() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).getParams().setLookbackBars(1);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U25.13 — блок параметров структуры опущен целиком: окно не проверяется")
    void u25_13_anAbsentParamsBlockIsNotCheckedForTheWindow() {
        CreateStrategyApiRequest request = reference();
        structure(request, REFERENCE_KEY).setParams(null);

        assertThat(violations(request)).isEmpty();
    }
}
