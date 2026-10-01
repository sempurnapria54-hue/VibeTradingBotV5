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
import java.math.BigDecimal;
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
 * бару предыдущего закрытия не даёт никто. Затравка — последнее записанное
 * значение ряда — подаётся только клетками `U9.10` и `U9.11`; прочие считают ряд без
 * неё (docs/spec/indicator-calculation.json, `obvSeed`, `obvNext`).
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
     * Непроставленный объём не читается нулём: бар 2 с выросшим закрытием
     * значения не имеет, бар 3 продолжает сумму от значения бара 1 без
     * вклада бара 2, а бар 4 с равным закрытием значение имеет — объём в
     * его шаг не входит.
     */
    @Test
    @DisplayName("U9.6 — объём баров 2 и 4 пуст: у бара 2 (рост) значения нет, бар 3 несёт 6+8=14, бар 4 (равное) — 14")
    void u9_6_anUnsetVolumeIsNotReadAsZero() {
        List<Candle> candles = series(new String[] {"100", "101", "102", "103", "103"},
                new String[] {"5", "6", "7", "8", "9"});
        candles.get(2).setVolume(null);
        candles.get(4).setVolume(null);

        List<IndicatorValue> values = calculate(candles);

        assertThat(values).extracting(IndicatorValue::getCandleTimestamp)
                .as("у бара с изменившимся закрытием и пустым объёмом значения нет")
                .containsExactly(barAt(1), barAt(3), barAt(4));
        assertThat(((ObvValue) values.get(0)).getObv()).isEqualByComparingTo("6");
        assertThat(((ObvValue) values.get(1)).getObv())
                .as("предыдущее определённое значение плюс собственный объём — вклада пропущенного бара нет")
                .isEqualByComparingTo("14");
        assertThat(((ObvValue) values.get(2)).getObv())
                .as("при равном закрытии значение есть и равно предыдущему")
                .isEqualByComparingTo("14");
    }

    /** Прогрев не переопределён — выведенный прогрев типа равен единице. */
    @Test
    @DisplayName("U9.9 — прогрев не переопределён: первое значение стои́т на баре 1 — втором баре ряда")
    void u9_9_theDerivedWarmupIsOneBar() {
        List<IndicatorValue> values = calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID,
                series(new String[] {"100", "101", "102"}, new String[] {"5", "6", "7"}), obvParams(null));

        assertThat(values).extracting(IndicatorValue::getCandleTimestamp)
                .containsExactly(barAt(1), barAt(2));
    }

    /**
     * Затравка — последнее записанное значение ряда на баре 2 — продолжает
     * сумму с бара после неё: бары до затравки и она сама значений не
     * получают, а первое значение равно затравке плюс знаковый объём своего
     * бара.
     */
    @Test
    @DisplayName("U9.10 — затравка 1000 на баре 2: значения только на барах 3 и 4 — 1000−4=996 и 996+5=1001")
    void u9_10_aSeedContinuesTheStoredSeriesFromTheBarAfterIt() {
        List<Candle> candles = series(new String[] {"100", "101", "103", "102", "104"},
                new String[] {"1", "2", "3", "4", "5"});
        ObvValue seed = new ObvValue();
        seed.setCandleTimestamp(barAt(2));
        seed.setObv(new BigDecimal("1000"));

        List<IndicatorValue> values = calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, obvParams(1),
                seed);

        assertThat(values).extracting(IndicatorValue::getCandleTimestamp)
                .as("на баре затравки и до него значений нет")
                .containsExactly(barAt(3), barAt(4));
        assertThat(((ObvValue) values.get(0)).getObv())
                .as("затравка плюс знаковый объём бара, а не нуль начала окна")
                .isEqualByComparingTo("996");
        assertThat(((ObvValue) values.get(1)).getObv()).isEqualByComparingTo("1001");
    }

    /**
     * Продолженный ряд прогрев уже прошёл в проходе, записавшем затравку, —
     * прогрев больше номера любого бара окна не отсекает ни одного бара после
     * затравки. Клетка охраняет ветвь затравки, которую `U9.10` с прогревом 1
     * не различает: там номер каждого бара после затравки и так не меньше
     * прогрева.
     */
    @Test
    @DisplayName("U9.11 — затравка 1000 на баре 1, прогрев 10 при пяти барах: значения на барах 2, 3, 4 — 1003, 999, 1004")
    void u9_11_aContinuedSeriesIsNotCutByAWarmupBeyondTheBarIndex() {
        List<Candle> candles = series(new String[] {"100", "101", "103", "102", "104"},
                new String[] {"1", "2", "3", "4", "5"});
        ObvValue seed = new ObvValue();
        seed.setCandleTimestamp(barAt(1));
        seed.setObv(new BigDecimal("1000"));

        List<IndicatorValue> values = calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, obvParams(10),
                seed);

        assertThat(values).extracting(IndicatorValue::getCandleTimestamp)
                .as("прогрев продолженного ряда не отсекает баров после затравки")
                .containsExactly(barAt(2), barAt(3), barAt(4));
        assertThat(((ObvValue) values.get(0)).getObv())
                .as("затравка плюс знаковый объём первого бара после неё")
                .isEqualByComparingTo("1003");
        assertThat(((ObvValue) values.get(1)).getObv()).isEqualByComparingTo("999");
        assertThat(((ObvValue) values.get(2)).getObv()).isEqualByComparingTo("1004");
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
