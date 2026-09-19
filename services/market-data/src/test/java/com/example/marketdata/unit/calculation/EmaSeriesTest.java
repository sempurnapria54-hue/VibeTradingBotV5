package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.emaParams;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.flatSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.EmaCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EmaParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EMA — выравнивание ряда и разгон: группа `U3` документа
 * `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/IndicatorValue.md §«Наследники (значения по
 * типу)» — состав поля).
 *
 * <p><b>Базовая сборка:</b> параметры EMA с объявленным периодом; ряд
 * закрытых свечей, различающихся ценой закрытия.
 *
 * <p><b>Арифметика дома не имеет</b> (M-1), поэтому клетки `U3.3`, `U3.5`
 * и `U3.6` кода не получили: их ожидание берётся только из реализации.
 * Прогоняемы состав поля, граница ряда и свойство постоянного ряда.
 */
class EmaSeriesTest {

    private final EmaCalculator calculator = new EmaCalculator();

    /** У наследника одно поле — сглаженная цена, и оно заполнено на каждом значении. */
    @Test
    @DisplayName("U3.1 — базовая сборка: у наследника ровно одно поле `ema`, заполненное на каждом значении")
    void u3_1_theHeirCarriesTheSmoothedPriceAndNothingElse() {
        assertThat(fieldNames(EmaValue.class))
                .as("иных полей у наследника нет")
                .containsExactly("ema");

        assertThat(calculate(risingSeries(12), emaParams(3, null)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((EmaValue) value).getEma()).isNotNull());
    }

    /** Порога длины у вычислителя нет — пустоту даёт разгон серии. */
    @Test
    @DisplayName("U3.2 — ряд из 3 свечей короче периода 5 при прогреве 0: ряд пуст, исключения нет")
    void u3_2_aHistoryShorterThanThePeriodYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(calculate(risingSeries(3), emaParams(5, 0))).isEmpty())
                .as("исключения нет: порога длины у вычислителя нет")
                .doesNotThrowAnyException();
    }

    /** Сглаживание постоянного ряда постоянно — свойство, а не число реализации. */
    @Test
    @DisplayName("U3.4 — ряд из 12 равных закрытий 100, период 3: все шесть значений равны 100")
    void u3_4_aConstantCloseSeriesSmoothesToThatVeryPrice() {
        List<IndicatorValue> values = calculate(flatSeries(12, "100"), emaParams(3, null));

        assertThat(values).hasSize(6)
                .allSatisfy(value -> assertThat(((EmaValue) value).getEma()).isEqualByComparingTo("100"));
    }

    // --- отклонения от базовой сборки --------------------------------------

    private List<IndicatorValue> calculate(List<Candle> candles, EmaParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, params);
    }
}
