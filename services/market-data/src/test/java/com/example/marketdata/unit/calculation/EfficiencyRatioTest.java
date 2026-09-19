package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.closeSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.efficiencyParams;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.EfficiencyRatioCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.EfficiencyRatioValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Эффективность хода — вторая формула с домом: группа `U10` документа
 * `.claude/tests/cases/market-data-indicators.md`.
 *
 * <p><b>Дом группы</b> — docs/models/domain/other/IndicatorValue.md §«Енум
 * `Type`»: он называет формулу отношением модуля чистого хода к сумме
 * модулей побарных ходов окна и объявляет нормировку. Выведенный прогрев
 * оконного типа равен периоду, и это тоже дом
 * (docs/rules/strategy-condition-contract.md §«Прогрев выводится, override
 * возможен»), — поэтому `U10.8` прогоняется.
 *
 * <p><b>Базовая сборка:</b> параметры эффективности хода с объявленным
 * периодом; ряд закрытых свечей. Клетка `U10.7` (нулевой знаменатель на
 * равных закрытиях) кода не получила: исхода на нём дом не называет.
 */
class EfficiencyRatioTest {

    private static final int PERIOD = 3;

    private final EfficiencyRatioCalculator calculator = new EfficiencyRatioCalculator();

    /** Чистый ход равен сумме побарных — отношение равно единице. */
    @Test
    @DisplayName("U10.1 — ряд монотонно растёт равными шагами: значение равно 1")
    void u10_1_aMonotonicRiseIsFullyEfficient() {
        assertThat(calculate(closeSeries("100", "101", "102", "103", "104"), efficiencyParams(PERIOD, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((EfficiencyRatioValue) value).getEfficiencyRatio())
                        .isEqualByComparingTo("1"));
    }

    /** В числителе модуль — направление на значение не влияет. */
    @Test
    @DisplayName("U10.2 — ряд монотонно падает равными шагами: значение равно 1")
    void u10_2_aMonotonicFallIsEquallyEfficientBecauseTheNumeratorIsAbsolute() {
        assertThat(calculate(closeSeries("104", "103", "102", "101", "100"), efficiencyParams(PERIOD, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((EfficiencyRatioValue) value).getEfficiencyRatio())
                        .isEqualByComparingTo("1"));
    }

    /** Ход, вернувшийся к исходной цене внутри окна, чистого хода не даёт. */
    @Test
    @DisplayName("U10.3 — ряд вернулся к исходной цене внутри окна 4: значение равно 0")
    void u10_3_aMoveReturningToItsStartHasNoNetProgress() {
        List<IndicatorValue> values = calculate(closeSeries("100", "101", "102", "101", "100"),
                efficiencyParams(4, 0));

        assertThat(values).hasSize(1);
        assertThat(((EfficiencyRatioValue) values.getFirst()).getEfficiencyRatio()).isEqualByComparingTo("0");
    }

    /** Величина нормирована по определению: за свои пределы не выходит. */
    @Test
    @DisplayName("U10.4 — произвольный ряд: значение лежит в пределах от 0 до 1 включительно")
    void u10_4_theValueIsNormalisedByDefinition() {
        assertThat(calculate(closeSeries("100", "103", "101", "104", "102", "105", "103"),
                efficiencyParams(PERIOD, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((EfficiencyRatioValue) value).getEfficiencyRatio())
                        .isBetween(BigDecimal.ZERO, BigDecimal.ONE));
    }

    /** У наследника одно поле — отношение. */
    @Test
    @DisplayName("U10.5 — базовая сборка: у наследника ровно одно поле `efficiencyRatio`, заполненное всегда")
    void u10_5_theHeirCarriesTheRatioAndNothingElse() {
        assertThat(fieldNames(EfficiencyRatioValue.class)).containsExactly("efficiencyRatio");

        assertThat(calculate(closeSeries("100", "101", "102", "103", "104"), efficiencyParams(PERIOD, 0)))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(((EfficiencyRatioValue) value).getEfficiencyRatio()).isNotNull());
    }

    /** Порог длины строгий: периода мало, нужен бар сверх него. */
    @Test
    @DisplayName("U10.6 — ряд из 3 свечей при периоде 3: ряд пуст, исключения нет")
    void u10_6_theLengthThresholdIsStrictAndNeedsOneBarAboveThePeriod() {
        assertThatCode(() -> assertThat(calculate(closeSeries("100", "101", "102"), efficiencyParams(PERIOD, 0)))
                .isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /** Выведенный прогрев оконного типа равен периоду — и это объявляет дом. */
    @Test
    @DisplayName("U10.8 — прогрев не переопределён: первое значение стои́т на баре 3, равном периоду")
    void u10_8_theDerivedWarmupOfAWindowedTypeEqualsThePeriod() {
        List<IndicatorValue> values = calculate(closeSeries("100", "101", "102", "103", "104", "105"),
                efficiencyParams(PERIOD, null));

        assertThat(values.getFirst().getCandleTimestamp()).isEqualTo(barAt(PERIOD));
    }

    /**
     * Деление точно по построению входа: ход {@code +3} и следом {@code −1}
     * дают чистый ход {@code 2} при сумме побарных {@code 4}.
     */
    @Test
    @DisplayName("U10.9 — окно, где чистый ход вдвое меньше суммы побарных: значение равно 0.5")
    void u10_9_aNetMoveHalfTheTotalMoveGivesOneHalf() {
        List<IndicatorValue> values = calculate(closeSeries("100", "103", "102"), efficiencyParams(2, 0));

        assertThat(values).hasSize(1);
        assertThat(((EfficiencyRatioValue) values.getFirst()).getEfficiencyRatio()).isEqualByComparingTo("0.5");
    }

    // --- отклонения от базовой сборки --------------------------------------

    private List<IndicatorValue> calculate(List<Candle> candles, EfficiencyRatioParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, params);
    }
}
