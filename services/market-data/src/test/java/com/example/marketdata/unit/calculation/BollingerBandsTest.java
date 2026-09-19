package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.bollingerParams;
import static com.example.marketdata.unit.calculation.CalcFixture.fieldNames;
import static com.example.marketdata.unit.calculation.CalcFixture.flatSeries;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.BollingerBandsCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.BollingerBandsValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.util.DomainMath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Полосы Боллинджера — инвариант порядка полос и производные: группа `U7`
 * документа `.claude/tests/cases/market-data-indicators.md`
 * (docs/models/domain/other/IndicatorValue.md §«Наследники (значения по
 * типу)»; точность промежуточного деления — docs/rules/decimal-arithmetic.md).
 *
 * <p><b>Базовая сборка:</b> параметры полос с периодом {@code 3} и
 * множителем отклонения {@code 2}; ряд закрытых свечей с растущей ценой
 * закрытия. Выведенный прогрев у оконного типа равен периоду, и это
 * <b>объявлено домом</b>, а не реализацией
 * (docs/rules/strategy-condition-contract.md §«Прогрев выводится, override
 * возможен»), — поэтому `U7.8` прогоняется.
 *
 * <p><b>Производные величины проверяются ИЗ САМИХ ПОЛЕЙ значения</b>, а не
 * пересчётом формулы: ширина обязана сходиться со своими операндами,
 * положение цены — со своими. Вид отклонения в корпусе не объявлен (M-1),
 * поэтому `U7.9` кода не получил, а у `U7.6` и `U7.7` не проверяется
 * клетка положения цены: её знаменатель нулевой, а константы вырожденного
 * окна дом не называет.
 */
class BollingerBandsTest {

    private static final int PERIOD = 3;

    private static final int BARS = 12;

    /** Цена закрытия бара ряда {@code risingSeries}: сотня плюс номер бара. */
    private static final int FIRST_CLOSE = 100;

    private final BollingerBandsCalculator calculator = new BollingerBandsCalculator();

    /** Пять полей значения обязательны: пустых среди них нет. */
    @Test
    @DisplayName("U7.1 — базовая сборка: у наследника ровно пять полей, и все пять заполнены на каждом значении")
    void u7_1_allFiveComponentsAreFilledOnEveryValue() {
        assertThat(fieldNames(BollingerBandsValue.class))
                .containsExactly("upperBand", "middleBand", "lowerBand", "bandwidth", "percentB");

        assertThat(base()).isNotEmpty().allSatisfy(value -> {
            BollingerBandsValue bands = (BollingerBandsValue) value;
            assertThat(bands.getUpperBand()).isNotNull();
            assertThat(bands.getMiddleBand()).isNotNull();
            assertThat(bands.getLowerBand()).isNotNull();
            assertThat(bands.getBandwidth()).isNotNull();
            assertThat(bands.getPercentB()).isNotNull();
        });
    }

    /** Инвариант порядка полос: верх не ниже середины, середина не ниже низа. */
    @Test
    @DisplayName("U7.2 — множитель 2 положителен: на каждом баре верх ≥ середина ≥ низ")
    void u7_2_theBandsKeepTheirOrderOnEveryBar() {
        assertThat(base()).isNotEmpty().allSatisfy(value -> {
            BollingerBandsValue bands = (BollingerBandsValue) value;
            assertThat(bands.getUpperBand()).isGreaterThanOrEqualTo(bands.getMiddleBand());
            assertThat(bands.getMiddleBand()).isGreaterThanOrEqualTo(bands.getLowerBand());
        });
    }

    /** Ширина согласована со своими операндами — проверяется из самих полей значения. */
    @Test
    @DisplayName("U7.3 — базовая сборка: ширина равна отношению расстояния между границами к середине")
    void u7_3_theBandwidthAgreesWithItsOwnOperands() {
        assertThat(base()).isNotEmpty().allSatisfy(value -> {
            BollingerBandsValue bands = (BollingerBandsValue) value;
            assertThat(bands.getBandwidth())
                    .as("бар %s", bands.getCandleTimestamp())
                    .isEqualByComparingTo(bands.getUpperBand().subtract(bands.getLowerBand())
                            .divide(bands.getMiddleBand(), DomainMath.CONTEXT));
        });
    }

    /**
     * Нормировка положения цены: единица достигается, когда закрытие равно
     * верхней границе, и ноль — когда нижней. <b>Проверяется формой
     * нормировки, а не подобранным входом:</b> конструировать равенство
     * закрытия и границы значило бы опереться на вид отклонения, которого
     * корпус не объявляет (M-1, клетка `U7.9`), — а из формы оба края
     * следуют сразу.
     */
    @Test
    @DisplayName("U7.4 — базовая сборка: положение цены равно доле закрытия между низом и верхом")
    void u7_4_thePricePositionIsNormalisedBetweenTheBands() {
        List<IndicatorValue> values = base();

        assertThat(values).isNotEmpty();
        for (int offset = 0; offset < values.size(); offset++) {
            BollingerBandsValue bands = (BollingerBandsValue) values.get(offset);
            BigDecimal close = BigDecimal.valueOf(FIRST_CLOSE + PERIOD + offset);
            BigDecimal span = bands.getUpperBand().subtract(bands.getLowerBand());
            assertThat(bands.getPercentB())
                    .as("бар %s: закрытие %s", bands.getCandleTimestamp(), close)
                    .isEqualByComparingTo(close.subtract(bands.getLowerBand()).divide(span, DomainMath.CONTEXT));
        }
    }

    /** Ряд короче периода — ряд пуст. */
    @Test
    @DisplayName("U7.5 — ряд из 2 свечей короче периода 3: ряд пуст, исключения нет")
    void u7_5_aHistoryShorterThanThePeriodYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(calculate(risingSeries(2), bollingerParams(PERIOD, "2", 0))).isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /** Постоянный ряд схлопывает полосы в середину; положение цены не проверяется. */
    @Test
    @DisplayName("U7.6 — ряд из равных закрытий 100: три полосы равны 100, ширина 0; положение цены не проверяется")
    void u7_6_aConstantCloseSeriesCollapsesTheBandsIntoTheMiddle() {
        List<IndicatorValue> values = calculate(flatSeries(BARS, "100"), bollingerParams(PERIOD, "2", null));

        assertThat(values).isNotEmpty().allSatisfy(value -> {
            BollingerBandsValue bands = (BollingerBandsValue) value;
            assertThat(bands.getMiddleBand()).isEqualByComparingTo("100");
            assertThat(bands.getUpperBand()).isEqualByComparingTo("100");
            assertThat(bands.getLowerBand()).isEqualByComparingTo("100");
            assertThat(bands.getBandwidth())
                    .as("делитель ширины — середина, и он не нулевой")
                    .isEqualByComparingTo("0");
        });
    }

    /** Нулевой множитель схлопывает полосы независимо от отклонения; положение цены не проверяется. */
    @Test
    @DisplayName("U7.7 — множитель отклонения 0: три полосы совпадают с серединой, ширина 0")
    void u7_7_aZeroMultiplierCollapsesTheBandsRegardlessOfTheDeviation() {
        List<IndicatorValue> values = calculate(risingSeries(BARS), bollingerParams(PERIOD, "0", null));

        assertThat(values).isNotEmpty().allSatisfy(value -> {
            BollingerBandsValue bands = (BollingerBandsValue) value;
            assertThat(bands.getUpperBand()).isEqualByComparingTo(bands.getMiddleBand());
            assertThat(bands.getLowerBand()).isEqualByComparingTo(bands.getMiddleBand());
            assertThat(bands.getBandwidth()).isEqualByComparingTo("0");
        });
    }

    /** Выведенный прогрев оконного типа равен периоду — и это объявляет дом. */
    @Test
    @DisplayName("U7.8 — прогрев не переопределён: первое значение стои́т на баре 3, равном периоду")
    void u7_8_theDerivedWarmupOfAWindowedTypeEqualsThePeriod() {
        assertThat(base().getFirst().getCandleTimestamp()).isEqualTo(barAt(PERIOD));
    }

    /**
     * Нулевая середина полосы: ветвь ширины отдаёт ноль, не деля.
     * <b>Клетка добрана под-шагом 3</b> по пробелу `G1` документа:
     * `U7.6` берёт равные <b>ненулевые</b> закрытия, а `U15.6` мерит нулевую
     * середину у структуры, не у полос. Число ширины ожиданием не является —
     * наблюдается отсутствие отказа и схлопывание полос.
     */
    @Test
    @DisplayName("U7.10 — весь ряд закрытий нулевой: три полосы равны нулю, ширина отдана нулём, отказа нет")
    void u7_10_aZeroMiddleBandYieldsTheWidthWithoutDividing() {
        assertThatCode(() -> assertThat(calculate(flatSeries(BARS, "0"), bollingerParams(PERIOD, "2", null)))
                .isNotEmpty()
                .allSatisfy(value -> {
                    BollingerBandsValue bands = (BollingerBandsValue) value;
                    assertThat(bands.getUpperBand()).isEqualByComparingTo("0");
                    assertThat(bands.getMiddleBand()).isEqualByComparingTo("0");
                    assertThat(bands.getLowerBand()).isEqualByComparingTo("0");
                    assertThat(bands.getBandwidth()).isEqualByComparingTo("0");
                }))
                .as("деления на ноль не происходит")
                .doesNotThrowAnyException();
    }

    // --- базовая сборка и отклонения ---------------------------------------

    private List<IndicatorValue> base() {
        return calculate(risingSeries(BARS), bollingerParams(PERIOD, "2", null));
    }

    private List<IndicatorValue> calculate(List<Candle> candles, BollingerBandsParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, params);
    }
}
