package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.atrParams;
import static com.example.marketdata.unit.calculation.CalcFixture.bar;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.AtrCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.AtrValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ATR — истинный диапазон и сглаживание: группа `U4` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/IndicatorValue.md §«Наследники (значения по
 * типу)» — состав поля и область значений).
 *
 * <p><b>Базовая сборка:</b> параметры ATR с объявленным периодом; ряд
 * свечей, у которых максимум, минимум и закрытие различимы.
 *
 * <p><b>Арифметика дома не имеет</b> (M-1): клетки `U4.5`-`U4.7` — разрыв
 * вверх, краевой бар и выведенный прогрев — кода не получили, потому что
 * их ожидание живёт только в javadoc реализации. Прогоняемы состав поля,
 * область значений, граница ряда и свойство постоянного размаха.
 */
class AtrSeriesTest {

    private final AtrCalculator calculator = new AtrCalculator();

    /** У наследника одно поле — средний истинный диапазон. */
    @Test
    @DisplayName("U4.1 — базовая сборка: у наследника ровно одно поле `atr`, заполненное на каждом значении")
    void u4_1_theHeirCarriesTheAverageTrueRangeAndNothingElse() {
        assertThat(fieldNames(AtrValue.class)).containsExactly("atr");

        assertThat(calculate(risingSeries(12), atrParams(3, null)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((AtrValue) value).getAtr()).isNotNull());
    }

    /** Диапазон есть расстояние, и отрицательным он не бывает ни на каком ряде. */
    @Test
    @DisplayName("U4.2 — произвольный ряд: все значения неотрицательны")
    void u4_2_everyValueIsNonNegativeBecauseARangeIsADistance() {
        assertThat(calculate(risingSeries(12), atrParams(3, null)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((AtrValue) value).getAtr()).isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }

    /** Ряд короче периода — ряд пуст. */
    @Test
    @DisplayName("U4.3 — ряд из 3 свечей короче периода 5: ряд пуст, исключения нет")
    void u4_3_aHistoryShorterThanThePeriodYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(calculate(risingSeries(3), atrParams(5, 0))).isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /**
     * Сглаживание постоянного ряда постоянно. <b>Вход выведен, а не
     * подобран:</b> у каждого бара размах равен {@code 10}, а закрытие
     * предыдущего лежит внутри размаха следующего — значит разрыва между
     * барами нет, и истинный диапазон каждого бара равен его размаху.
     */
    @Test
    @DisplayName("U4.4 — у каждого бара размах 10, разрывов нет: все значения равны 10")
    void u4_4_aConstantTrueRangeSmoothesToThatVeryNumber() {
        List<IndicatorValue> values = calculate(constantRangeSeries(12), atrParams(3, null));

        assertThat(values).hasSize(6)
                .allSatisfy(value -> assertThat(((AtrValue) value).getAtr()).isEqualByComparingTo("10"));
    }

    /** Ряд длиной ровно в период даёт единственное значение — если прогрев его не отсёк. */
    @Test
    @DisplayName("U4.8 — ряд из 3 свечей, период 3: при прогреве 0 одно значение на баре 2, при выведенном — ни одного")
    void u4_8_aHistoryExactlyThePeriodYieldsItsLastBarOnlyWhenTheWarmupAllowsIt() {
        List<IndicatorValue> withZeroWarmup = calculate(risingSeries(3), atrParams(3, 0));

        assertThat(withZeroWarmup).hasSize(1);
        assertThat(withZeroWarmup.getFirst().getCandleTimestamp()).isEqualTo(barAt(2));

        assertThat(calculate(risingSeries(3), atrParams(3, null)))
                .as("выведенный прогрев отсекает единственное значение целиком")
                .isEmpty();
    }

    // --- отклонения от базовой сборки --------------------------------------

    private List<IndicatorValue> calculate(List<Candle> candles, AtrParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, params);
    }

    /** Ряд одинаковых баров: максимум 110, минимум 100, закрытие 105 — размах 10 и ни одного разрыва. */
    private static List<Candle> constantRangeSeries(int count) {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            candles.add(bar(index, "110", "100", "105", "1"));
        }
        return candles;
    }
}
