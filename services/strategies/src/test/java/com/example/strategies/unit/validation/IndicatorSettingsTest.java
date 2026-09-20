package com.example.strategies.unit.validation;

import static com.example.strategies.unit.validation.ValidationFixture.indicator;
import static com.example.strategies.unit.validation.ValidationFixture.indicators;
import static com.example.strategies.unit.validation.ValidationFixture.matching;
import static com.example.strategies.unit.validation.ValidationFixture.newIndicator;
import static com.example.strategies.unit.validation.ValidationFixture.reference;
import static com.example.strategies.unit.validation.ValidationFixture.violations;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.strategy.EmaParamsApiModel;
import com.example.strategies.api.model.strategy.IndicatorParamsApiModel;
import com.example.strategies.api.model.strategy.MacdParamsApiModel;
import com.example.strategies.api.model.strategy.ObvParamsApiModel;
import com.example.strategies.api.model.strategy.StochasticParamsApiModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Настройки индикаторов: ключи, прогрев, длительность — группа
 * {@code U24} документа
 * `.claude/tests/cases/strategy-definition-validation.md` (дом —
 * docs/rules/strategy-condition-contract.md §«Прогрев выводится,
 * override возможен»; минимум по типу параметров — звено
 * {@code warmupFloor}).
 *
 * <p><b>Минимум прогрева выводится из ТИПА ПАРАМЕТРОВ, а не из типа
 * индикатора:</b> у оконных он равен периоду, у схождения-расхождения —
 * сумме двух окон, у стохастика — сумме трёх, у прочих — единице. Каждая
 * ветвь берётся своим кейсом: одна проверяла бы только ту, которую несёт
 * эталон.
 *
 * <p><b>Неразобранный тип индикатора уносит ключ из карты типов</b>, и
 * ссылки на него перестают резолвиться — вторая половина ожидания клетки.
 */
class IndicatorSettingsTest {

    private static final String BELOW_MINIMUM = "is below derived minimum";

    @Test
    @DisplayName("U24.1 — базовая сборка: шесть настроек с уникальными ключами")
    void u24_1_theReferenceCatalogueIsWellFormed() {
        assertThat(violations(reference())).isEmpty();
    }

    @Test
    @DisplayName("U24.2 — вторая настройка несёт ключ первой: дубль с названным ключом")
    void u24_2_aReusedIndicatorKeyIsRejected() {
        CreateStrategyApiRequest request = reference();
        indicators(request).add(newIndicator("atr_15m", "ATR", null));

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains("duplicate indicator setting key atr_15m");
    }

    @Test
    @DisplayName("U24.3 — тип индикатора неизвестен: настройка выпадает из карты типов")
    void u24_3_anUnknownIndicatorTypeBreaksEveryReferenceToItsKey() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setIndicatorType("SUPERTREND");

        List<String> violations = violations(request);

        assertThat(matching(violations, "indicatorType: unknown value SUPERTREND")).hasSize(1);
        assertThat(matching(violations, "references unknown indicator setting key atr_15m"))
                .as("в карту типов настройка с неразобранным типом не попадает")
                .isNotEmpty();
    }

    @Test
    @DisplayName("U24.4 — назначение настройки — неизвестная строка: отказ перечня")
    void u24_4_anUnknownDestinyIsRejected() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setDestiny("SIGNAL");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".destiny: unknown value SIGNAL");
    }

    @Test
    @DisplayName("U24.5 — прогрев ниже периода: переопределение ниже выводимого минимума")
    void u24_5_aWarmupBelowThePeriodIsRejected() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "ema_fast_15m").getParams().setWarmup(5);

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".warmup: override 5 " + BELOW_MINIMUM + " 10");
    }

    @Test
    @DisplayName("U24.6 — прогрев равен периоду: граница включена")
    void u24_6_theWarmupBoundIsInclusive() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "ema_fast_15m").getParams().setWarmup(10);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U24.7 — прогрев объявлен, период опущен: минимум не выводится")
    void u24_7_anAbsentPeriodLeavesNothingToCompareWith() {
        CreateStrategyApiRequest request = reference();
        EmaParamsApiModel params = (EmaParamsApiModel) indicator(request, "ema_fast_15m").getParams();
        params.setPeriod(null);
        params.setWarmup(5);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U24.8 — у схождения-расхождения минимум — сумма медленного и сигнального окон")
    void u24_8_theMacdFloorIsTheSumOfTwoWindows() {
        MacdParamsApiModel params = new MacdParamsApiModel();
        params.setTimeframe("ONE_HOUR");
        params.setFastPeriod(12);
        params.setSlowPeriod(26);
        params.setSignalPeriod(9);
        params.setWarmup(20);

        assertThat(violationsWithExtraIndicator("macd_1h", "MACD", params))
                .singleElement()
                .asString()
                .contains("override 20 " + BELOW_MINIMUM + " 35");
    }

    @Test
    @DisplayName("U24.9 — у стохастика минимум — сумма трёх окон")
    void u24_9_theStochasticFloorIsTheSumOfThreeWindows() {
        StochasticParamsApiModel params = new StochasticParamsApiModel();
        params.setTimeframe("ONE_HOUR");
        params.setkPeriod(14);
        params.setdPeriod(3);
        params.setSmoothPeriod(3);
        params.setWarmup(10);

        assertThat(violationsWithExtraIndicator("stoch_1h", "STOCHASTIC", params))
                .singleElement()
                .asString()
                .contains("override 10 " + BELOW_MINIMUM + " 20");
    }

    @Test
    @DisplayName("U24.10 — у типа без окна минимум — единица")
    void u24_10_theDefaultFloorIsOne() {
        ObvParamsApiModel params = new ObvParamsApiModel();
        params.setTimeframe("ONE_HOUR");
        params.setWarmup(0);

        assertThat(violationsWithExtraIndicator("obv_1h", "OBV", params))
                .singleElement()
                .asString()
                .contains("override 0 " + BELOW_MINIMUM + " 1");
    }

    @Test
    @DisplayName("U24.11 — срок годности не разбирается как длительность")
    void u24_11_anUnparsableDurationIsRejected() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setExpirationDuration("два часа");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".expirationDuration: invalid ISO-8601 duration два часа");
    }

    @Test
    @DisplayName("U24.12 — срок годности опущен: разбор идёт только при непустоте")
    void u24_12_anAbsentDurationIsNotParsed() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setExpirationDuration(null);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("U24.13 — таймфрейм параметров — неизвестная строка: отказ перечня")
    void u24_13_anUnknownParamsTimeframeIsRejected() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").getParams().setTimeframe("TEN_MINUTES");

        assertThat(violations(request))
                .singleElement()
                .asString()
                .contains(".params.timeframe: unknown value TEN_MINUTES");
    }

    @Test
    @DisplayName("U24.14 — блок параметров опущен вовсе: ни таймфрейм, ни прогрев не проверяются")
    void u24_14_anAbsentParamsBlockSilencesBothChecks() {
        CreateStrategyApiRequest request = reference();
        indicator(request, "atr_15m").setParams(null);

        assertThat(violations(request)).isEmpty();
    }

    /** Нарушения дерева с добавленной настройкой: ссылок на неё не заводится ни одной. */
    private List<String> violationsWithExtraIndicator(String key, String type, IndicatorParamsApiModel params) {
        CreateStrategyApiRequest request = reference();
        indicators(request).add(newIndicator(key, type, params));
        return violations(request);
    }
}
