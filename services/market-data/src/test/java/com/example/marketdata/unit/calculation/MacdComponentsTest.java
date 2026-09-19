package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.flatSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.macdParams;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.MacdCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.MacdValue;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MACD — три поля одного значения и порядок периодов: группа `U6`
 * документа `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/IndicatorValue.md §«Наследники (значения по
 * типу)», §«Адресный компонент в условии (D1)»).
 *
 * <p><b>Базовая сборка:</b> параметры MACD с быстрым {@code 3}, медленным
 * {@code 6} и сигнальным {@code 3} периодами; ряд закрытых свечей длиннее
 * суммы медленного и сигнального.
 *
 * <p><b>Инвариант согласованности компонентов проверяется без знания
 * формулы линий</b> (`U6.2`): компоненты адресуются порознь, и гистограмма
 * обязана сходиться со своими операндами на том же баре. Клетки `U6.6`
 * (выведенный прогрев) и `U6.7`-`U6.8` (порядок периодов, который не
 * охраняет никто, — M-7) кода не получили.
 */
class MacdComponentsTest {

    private static final int FAST = 3;

    private static final int SLOW = 6;

    private static final int SIGNAL = 3;

    private final MacdCalculator calculator = new MacdCalculator();

    /** Три поля значения обязательны: пустых среди них нет ни на одном баре. */
    @Test
    @DisplayName("U6.1 — базовая сборка: у наследника ровно три поля, и все три заполнены на каждом значении")
    void u6_1_allThreeComponentsAreFilledOnEveryValue() {
        assertThat(fieldNames(MacdValue.class)).containsExactly("macdLine", "signalLine", "histogram");

        assertThat(base()).isNotEmpty().allSatisfy(value -> {
            MacdValue macd = (MacdValue) value;
            assertThat(macd.getMacdLine()).isNotNull();
            assertThat(macd.getSignalLine()).isNotNull();
            assertThat(macd.getHistogram()).isNotNull();
        });
    }

    /** Гистограмма сходится со своими операндами на том же баре. */
    @Test
    @DisplayName("U6.2 — базовая сборка: гистограмма равна разности линии и сигнальной на том же баре")
    void u6_2_theHistogramAgreesWithItsOwnOperandsOnTheSameBar() {
        assertThat(base()).isNotEmpty().allSatisfy(value -> {
            MacdValue macd = (MacdValue) value;
            assertThat(macd.getHistogram())
                    .as("бар %s", macd.getCandleTimestamp())
                    .isEqualByComparingTo(macd.getMacdLine().subtract(macd.getSignalLine()));
        });
    }

    /** Ряд короче медленного периода — ряд пуст. */
    @Test
    @DisplayName("U6.3 — ряд из 5 свечей короче медленного периода 6: ряд пуст, исключения нет")
    void u6_3_aHistoryShorterThanTheSlowPeriodYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(calculate(risingSeries(5), macdParams(FAST, SLOW, SIGNAL, 0))).isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /** На постоянном ряде обе линии и их разность нулевые. */
    @Test
    @DisplayName("U6.4 — ряд из 14 равных закрытий: линия, сигнальная и гистограмма равны нулю на всех барах")
    void u6_4_aConstantCloseSeriesZeroesAllThreeComponents() {
        List<IndicatorValue> values = calculate(flatSeries(14, "100"), macdParams(FAST, SLOW, SIGNAL, null));

        assertThat(values).isNotEmpty().allSatisfy(value -> {
            MacdValue macd = (MacdValue) value;
            assertThat(macd.getMacdLine()).isEqualByComparingTo("0");
            assertThat(macd.getSignalLine()).isEqualByComparingTo("0");
            assertThat(macd.getHistogram()).isEqualByComparingTo("0");
        });
    }

    /** Значения с пустой сигнальной в ряд не идут: три поля обязательны. */
    @Test
    @DisplayName("U6.5 — прогрев 0: ряд начинается с бара 7, где определена сигнальная, а не с бара 5")
    void u6_5_theSeriesStartsNoEarlierThanTheSignalLineIsDefined() {
        List<IndicatorValue> values = calculate(risingSeries(20), macdParams(FAST, SLOW, SIGNAL, 0));

        assertThat(values.getFirst().getCandleTimestamp())
                .as("бар 5 несёт линию, но сигнальная там ещё пуста")
                .isEqualTo(barAt(SLOW - 1 + SIGNAL - 1));
    }

    // --- базовая сборка и отклонения ---------------------------------------

    private List<IndicatorValue> base() {
        return calculate(risingSeries(20), macdParams(FAST, SLOW, SIGNAL, null));
    }

    private List<IndicatorValue> calculate(List<Candle> candles, MacdParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, params);
    }
}
