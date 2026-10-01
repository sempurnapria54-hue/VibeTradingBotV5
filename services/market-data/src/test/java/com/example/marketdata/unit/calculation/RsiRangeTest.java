package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.closeSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.flatSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.rsiParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.RsiCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.RsiValue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RSI — область значений и края: группа `U5` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/IndicatorValue.md §«Наследники (значения по
 * типу)»).
 *
 * <p><b>Базовая сборка:</b> параметры RSI с объявленным периодом; ряд
 * закрытых свечей.
 *
 * <p><b>Ожидание здесь — СВОЙСТВО, и оно не слабее числа.</b> «Осциллятор
 * в своих пределах» падает на перепутанных местами приросте и убытке ровно
 * так же, как падало бы точное число, и не падает на смене последнего
 * разряда деления — то есть мерит предмет, а не арифметику библиотеки.
 * Исход вырожденного окна (`U5.6`) и выведенный прогрев (`U5.7`) берутся из
 * спеки: docs/spec/indicator-calculation.json (`rsiValue`, `derivedWarmup`).
 */
class RsiRangeTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RsiCalculator calculator = new RsiCalculator();

    /** У наследника одно поле — значение осциллятора. */
    @Test
    @DisplayName("U5.1 — базовая сборка: у наследника ровно одно поле `rsi`, заполненное на каждом значении")
    void u5_1_theHeirCarriesTheOscillatorValueAndNothingElse() {
        assertThat(fieldNames(RsiValue.class)).containsExactly("rsi");

        assertThat(calculate(risingSeries(12), rsiParams(3, null)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((RsiValue) value).getRsi()).isNotNull());
    }

    /** Нормированный осциллятор не выходит за свои пределы ни на каком ряде. */
    @Test
    @DisplayName("U5.2 — произвольный ряд: каждое значение лежит в пределах от 0 до 100 включительно")
    void u5_2_everyValueStaysWithinTheOscillatorBounds() {
        assertThat(calculate(alternatingSeries(), rsiParams(3, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((RsiValue) value).getRsi())
                        .isBetween(BigDecimal.ZERO, HUNDRED));
    }

    /** Убытка в окне нет — осциллятор стои́т на верхней границе. */
    @Test
    @DisplayName("U5.3 — ряд растёт на каждом баре: все значения равны 100")
    void u5_3_aSeriesWithoutLossesSitsOnTheUpperBound() {
        assertThat(calculate(risingSeries(12), rsiParams(3, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((RsiValue) value).getRsi()).isEqualByComparingTo("100"));
    }

    /** Прироста в окне нет — осциллятор стои́т на нижней границе. */
    @Test
    @DisplayName("U5.4 — ряд падает на каждом баре: все значения равны 0")
    void u5_4_aSeriesWithoutGainsSitsOnTheLowerBound() {
        assertThat(calculate(fallingSeries(), rsiParams(3, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((RsiValue) value).getRsi()).isEqualByComparingTo("0"));
    }

    /** Порог длины строгий: периода мало, нужен бар сверх него. */
    @Test
    @DisplayName("U5.5 — ряд из 3 свечей при периоде 3: ряд пуст, исключения нет")
    void u5_5_theLengthThresholdIsStrictAndNeedsOneBarAboveThePeriod() {
        assertThatCode(() -> assertThat(calculate(risingSeries(3), rsiParams(3, 0))).isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /**
     * Плоский ряд — вырожденное окно: ни прироста, ни убыли, и значение
     * стои́т на середине шкалы, а не на верхней границе — иначе рынок,
     * который не двигался, читался бы предельной перекупленностью
     * (docs/spec/indicator-calculation.json, величина `rsiValue`).
     */
    @Test
    @DisplayName("U5.6 — ряд из 12 равных закрытий: все значения равны 50, а не 100")
    void u5_6_aFlatSeriesSitsInTheMiddleOfTheScale() {
        assertThat(calculate(flatSeries(12, "100"), rsiParams(3, 0)))
                .hasSize(9)
                .allSatisfy(value -> assertThat(((RsiValue) value).getRsi()).isEqualByComparingTo("50"));
    }

    /**
     * Выведенный прогрев — три периода: сглаживание Уайлдера вдвое
     * медленнее EMA (docs/spec/indicator-calculation.json, величина
     * `derivedWarmup`).
     */
    @Test
    @DisplayName("U5.7 — прогрев не переопределён, период 3: ряд начинается с бара 9")
    void u5_7_theDerivedWarmupIsThreePeriods() {
        List<IndicatorValue> values = calculate(risingSeries(20), rsiParams(3, null));

        assertThat(values).hasSize(11);
        assertThat(values.getFirst().getCandleTimestamp()).isEqualTo(barAt(9));
    }

    /** Есть и прирост, и убыток — значение строго между границами. */
    @Test
    @DisplayName("U5.8 — половина баров растёт, половина падает равными шагами: значение строго между 0 и 100")
    void u5_8_bothGainsAndLossesPutTheValueStrictlyBetweenTheBounds() {
        assertThat(calculate(alternatingSeries(), rsiParams(3, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((RsiValue) value).getRsi())
                        .as("точное число не проверяется: его дома нет")
                        .isStrictlyBetween(BigDecimal.ZERO, HUNDRED));
    }

    // --- отклонения от базовой сборки --------------------------------------

    private List<IndicatorValue> calculate(List<Candle> candles, RsiParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, params);
    }

    /** Ряд, падающий на каждом баре равными шагами. */
    private static List<Candle> fallingSeries() {
        return closeSeries("111", "110", "109", "108", "107", "106", "105", "104", "103", "102", "101", "100");
    }

    /** Ряд, где прирост и убыток равными шагами чередуются на каждом баре. */
    private static List<Candle> alternatingSeries() {
        return closeSeries("100", "101", "100", "101", "100", "101", "100", "101", "100", "101", "100", "101");
    }
}
