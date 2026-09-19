package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.bar;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.stochasticParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.StochasticCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StochasticParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.StochasticValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Стохастик — две линии и их выравнивание: группа `U8` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/IndicatorValue.md §«Наследники (значения по
 * типу)», §«Адресный компонент в условии (D1)»).
 *
 * <p><b>Базовая сборка:</b> параметры стохастика с окном {@code 3},
 * сглаживанием {@code 2} и окном второй линии {@code 2}; ряд свечей с
 * различимыми максимумом, минимумом и закрытием.
 *
 * <p><b>Экстремумы берутся по ОКНУ, а не по бару</b>, и входы краёв
 * выведены из этого: в клетке `U8.3` закрытие каждого бара есть максимум
 * своего окна лишь потому, что максимумы не убывают, а окна сглаживания
 * единичны. Клетки `U8.7` (нулевой размах окна) и `U8.8` (выведенный
 * прогрев) кода не получили: их ожидание живёт только в реализации (M-1).
 */
class StochasticLinesTest {

    private static final int K_PERIOD = 3;

    private static final int SMOOTH_PERIOD = 2;

    private static final int D_PERIOD = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final StochasticCalculator calculator = new StochasticCalculator();

    /** Два поля значения обязательны: пустых среди них нет. */
    @Test
    @DisplayName("U8.1 — базовая сборка: у наследника ровно два поля, и оба заполнены на каждом значении")
    void u8_1_bothLinesAreFilledOnEveryValue() {
        assertThat(fieldNames(StochasticValue.class)).containsExactly("k", "d");

        assertThat(base()).isNotEmpty().allSatisfy(value -> {
            StochasticValue stochastic = (StochasticValue) value;
            assertThat(stochastic.getK()).isNotNull();
            assertThat(stochastic.getD()).isNotNull();
        });
    }

    /** Нормированный осциллятор: обе линии не выходят за свои пределы. */
    @Test
    @DisplayName("U8.2 — произвольный ряд: обе линии лежат в пределах от 0 до 100 включительно")
    void u8_2_bothLinesStayWithinTheOscillatorBounds() {
        assertThat(base()).isNotEmpty().allSatisfy(value -> {
            StochasticValue stochastic = (StochasticValue) value;
            assertThat(stochastic.getK()).isBetween(BigDecimal.ZERO, HUNDRED);
            assertThat(stochastic.getD()).isBetween(BigDecimal.ZERO, HUNDRED);
        });
    }

    /** Числитель равен знаменателю — быстрая линия стои́т на верхней границе. */
    @Test
    @DisplayName("U8.3 — закрытие равно максимуму бара, максимумы не убывают, сглаживание единичное: быстрая равна 100")
    void u8_3_aCloseAtTheWindowHighPutsTheFastLineOnTheUpperBound() {
        List<IndicatorValue> values = calculate(closeAtWindowHighSeries(),
                stochasticParams(K_PERIOD, 1, 1, 0));

        assertThat(values).isNotEmpty().allSatisfy(value ->
                assertThat(((StochasticValue) value).getK()).isEqualByComparingTo("100"));
    }

    /** Числитель нулевой — быстрая линия стои́т на нижней границе. */
    @Test
    @DisplayName("U8.4 — закрытие равно минимуму бара, минимумы не возрастают, сглаживание единичное: быстрая равна 0")
    void u8_4_aCloseAtTheWindowLowPutsTheFastLineOnTheLowerBound() {
        List<IndicatorValue> values = calculate(closeAtWindowLowSeries(),
                stochasticParams(K_PERIOD, 1, 1, 0));

        assertThat(values).isNotEmpty().allSatisfy(value ->
                assertThat(((StochasticValue) value).getK()).isEqualByComparingTo("0"));
    }

    /** Ряд короче окна — ряд пуст. */
    @Test
    @DisplayName("U8.5 — ряд из 3 свечей короче окна 5: ряд пуст, исключения нет")
    void u8_5_aHistoryShorterThanTheWindowYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(calculate(risingSeries(3), stochasticParams(5, 2, 2, 0))).isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /** Бара, где медленная линия ещё пуста, в ряду нет: оба поля обязательны. */
    @Test
    @DisplayName("U8.6 — прогрев 0: ряд начинается с бара 4, где определены обе линии, а не с бара 3")
    void u8_6_aBarWhereTheSlowLineIsStillEmptyDoesNotEnterTheSeries() {
        List<IndicatorValue> values = calculate(risingSeries(20),
                stochasticParams(K_PERIOD, D_PERIOD, SMOOTH_PERIOD, 0));

        assertThat(values.getFirst().getCandleTimestamp())
                .as("бар 3 несёт быструю линию, медленная там ещё пуста")
                .isEqualTo(barAt(4));
    }

    /**
     * Член сглаживания остаётся пустым, пока его окно неполно: сглаживание
     * идёт поверх ряда, у которого внутри окна ещё есть пустота.
     * <b>Клетка добрана под-шагом 3</b> по пробелу `G3` документа: прежде
     * ветвь наблюдалась только косвенно, итоговым рядом (`U8.6`).
     */
    @Test
    @DisplayName("U8.9 — окно 3, сглаживание 3, вторая линия 1: ряд начинается с бара 4, бары 2 и 3 пропущены")
    void u8_9_aSmoothingMemberStaysEmptyWhileItsWindowIsIncomplete() {
        List<IndicatorValue> values = calculate(risingSeries(12), stochasticParams(K_PERIOD, 1, 3, 0));

        assertThat(values.getFirst().getCandleTimestamp())
                .as("бары 2 и 3 несут сырое значение, но окно сглаживания там ещё неполно")
                .isEqualTo(barAt(4));
        assertThat(values).hasSize(8);
    }

    // --- базовая сборка и отклонения ---------------------------------------

    private List<IndicatorValue> base() {
        return calculate(risingSeries(20), stochasticParams(K_PERIOD, D_PERIOD, SMOOTH_PERIOD, null));
    }

    private List<IndicatorValue> calculate(List<Candle> candles, StochasticParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, params);
    }

    /** Закрытие равно максимуму своего бара, а максимумы не убывают: закрытие есть максимум окна. */
    private static List<Candle> closeAtWindowHighSeries() {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            String high = String.valueOf(100 + index);
            candles.add(bar(index, high, String.valueOf(90 + index), high, "1"));
        }
        return candles;
    }

    /** Закрытие равно минимуму своего бара, а минимумы не возрастают: закрытие есть минимум окна. */
    private static List<Candle> closeAtWindowLowSeries() {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            String low = String.valueOf(100 - index);
            candles.add(bar(index, String.valueOf(110 - index), low, low, "1"));
        }
        return candles;
    }
}
