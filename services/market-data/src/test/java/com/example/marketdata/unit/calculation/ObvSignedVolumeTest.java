package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.bar;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.obvParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.ObvCalculator;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.indicator.ObvValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Знаковая сумма объёма — единственная формула с домом: группа `U9`
 * документа `.claude/tests/cases/market-data-indicators.md`.
 *
 * <p><b>Дом группы</b> — docs/models/domain/other/IndicatorValue.md §«Енум
 * `Type`»: он называет формулу словами (прирост объёма при росте цены
 * закрытия, убыль при падении, ноль при равенстве) и там же объявляет
 * нестабильность абсолютного уровня. Поэтому здесь ожидания — числа, а не
 * свойства: деления в формуле нет вовсе.
 *
 * <p><b>Базовая сборка:</b> параметры знаковой суммы объёма; ряд закрытых
 * свечей с объявленным объёмом; прогрев переопределён единицей — первому
 * бару предыдущего закрытия не даёт никто.
 *
 * <p>Клетка `U9.6` (непроставленный объём читается нулём — M-8) и `U9.9`
 * (класс выведения прогрева у кумулятивного типа в доме отсутствует — M-1)
 * кода не получили.
 */
class ObvSignedVolumeTest {

    private final ObvCalculator calculator = new ObvCalculator();

    /** Рост закрытия прибавляет ровно объём своего бара. */
    @Test
    @DisplayName("U9.1 — закрытие бара 2 выше предыдущего, объём 7: накопленное выросло с 0 до 7")
    void u9_1_aRisingCloseAddsExactlyTheVolumeOfItsBar() {
        List<IndicatorValue> values = calculate(series(new String[] {"100", "100", "101"},
                new String[] {"5", "6", "7"}));

        assertThat(values).hasSize(2);
        assertThat(((ObvValue) values.get(0)).getObv()).isEqualByComparingTo("0");
        assertThat(((ObvValue) values.get(1)).getObv())
                .as("прирост равен объёму бара, а не его цене")
                .isEqualByComparingTo("7");
    }

    /** Падение закрытия отнимает ровно объём своего бара. */
    @Test
    @DisplayName("U9.2 — закрытие бара 2 ниже предыдущего, объём 7: накопленное упало с 0 до −7")
    void u9_2_aFallingCloseSubtractsExactlyTheVolumeOfItsBar() {
        List<IndicatorValue> values = calculate(series(new String[] {"100", "100", "99"},
                new String[] {"5", "6", "7"}));

        assertThat(values).hasSize(2);
        assertThat(((ObvValue) values.get(1)).getObv()).isEqualByComparingTo("-7");
    }

    /** Равенство закрытий не меняет накопленного, но значение на бар всё равно выдано. */
    @Test
    @DisplayName("U9.3 — закрытие бара 2 равно предыдущему: накопленное осталось 6, значение на бар выдано")
    void u9_3_anUnchangedCloseKeepsTheRunningSumAndStillEmitsAValue() {
        List<IndicatorValue> values = calculate(series(new String[] {"100", "101", "101"},
                new String[] {"5", "6", "7"}));

        assertThat(values).hasSize(2);
        assertThat(((ObvValue) values.get(1)).getObv()).isEqualByComparingTo("6");
        assertThat(values.get(1).getCandleTimestamp())
                .as("значение на равный бар выдано, а не пропущено")
                .isEqualTo(barAt(2));
    }

    /** Первому бару предыдущего закрытия не даёт никто — знака у его объёма нет. */
    @Test
    @DisplayName("U9.4 — ряд из двух баров, прогрев 1: единственное значение стои́т на баре 1")
    void u9_4_theFirstBarHasNoPreviousCloseAndThereforeNoSign() {
        List<IndicatorValue> values = calculate(series(new String[] {"100", "101"}, new String[] {"5", "6"}));

        assertThat(values).hasSize(1);
        assertThat(values.getFirst().getCandleTimestamp()).isEqualTo(barAt(1));
    }

    /** Ряд из одного бара — ряд пуст. */
    @Test
    @DisplayName("U9.5 — ряд из одного бара: ряд пуст, исключения нет")
    void u9_5_aSingleBarHistoryYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(calculate(series(new String[] {"100"}, new String[] {"5"}))).isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /**
     * Абсолютный уровень зависит от начала ряда — и дом объявляет это прямо.
     * <b>Бар подан буквально тот же:</b> короткий ряд есть хвост длинного,
     * то есть те же объекты свечей с теми же отметками времени.
     */
    @Test
    @DisplayName("U9.7 — один и тот же бар 3 в рядах длиной 4 и 3: значения 3 и 1 различаются")
    void u9_7_theAbsoluteLevelDependsOnWhereTheSeriesStarts() {
        List<Candle> longer = series(new String[] {"100", "102", "101", "103"},
                new String[] {"1", "2", "3", "4"});
        List<Candle> shorter = longer.subList(1, longer.size());

        List<IndicatorValue> fromLonger = calculate(longer);
        List<IndicatorValue> fromShorter = calculate(shorter);

        assertThat(fromLonger.getLast().getCandleTimestamp())
                .as("наблюдается один и тот же бар")
                .isEqualTo(fromShorter.getLast().getCandleTimestamp());
        assertThat(((ObvValue) fromLonger.getLast()).getObv()).isEqualByComparingTo("3");
        assertThat(((ObvValue) fromShorter.getLast()).getObv())
                .as("уровень зависит от начала ряда: нестабильность объявлена домом")
                .isEqualByComparingTo("1");
    }

    /** Чередование роста и падения равными объёмами возвращает накопленное к исходному. */
    @Test
    @DisplayName("U9.8 — рост и падение чередуются объёмом 10: накопленное возвращается к нулю через пару баров")
    void u9_8_alternatingEqualVolumesReturnTheRunningSumToItsStart() {
        List<IndicatorValue> values = calculate(series(new String[] {"100", "101", "100", "101", "100"},
                new String[] {"10", "10", "10", "10", "10"}));

        assertThat(values).hasSize(4);
        assertThat(((ObvValue) values.get(0)).getObv()).isEqualByComparingTo("10");
        assertThat(((ObvValue) values.get(1)).getObv()).isEqualByComparingTo("0");
        assertThat(((ObvValue) values.get(3)).getObv())
                .as("пара баров возвращает накопленное к исходному нулю")
                .isEqualByComparingTo("0");
    }

    // --- базовая сборка ----------------------------------------------------

    private List<IndicatorValue> calculate(List<Candle> candles) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, obvParams(1));
    }

    private static List<Candle> series(String[] closes, String[] volumes) {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            candles.add(bar(index, closes[index], closes[index], closes[index], volumes[index]));
        }
        return candles;
    }
}
