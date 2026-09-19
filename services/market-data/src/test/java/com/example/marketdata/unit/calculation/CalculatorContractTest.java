package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.COMPUTATION_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.INSTRUMENT_ID;
import static com.example.marketdata.unit.calculation.CalcFixture.allCalculators;
import static com.example.marketdata.unit.calculation.CalcFixture.barAt;
import static com.example.marketdata.unit.calculation.CalcFixture.barsAt;
import static com.example.marketdata.unit.calculation.CalcFixture.emaParams;
import static com.example.marketdata.unit.calculation.CalcFixture.paramsFor;
import static com.example.marketdata.unit.calculation.CalcFixture.risingSeries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.domain.service.indicator.EmaCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Общий контракт вычислителя — прогрев, отметка времени, идентичности:
 * группа `U1` документа `.claude/tests/cases/market-data-indicators.md`
 * (docs/components/IndicatorJob.md §Прогрев;
 * docs/rules/strategy-condition-contract.md §«Прогрев выводится, override
 * возможен»; docs/models/domain/other/IndicatorValue.md §Структура).
 *
 * <p><b>Базовая сборка:</b> ряд закрытых свечей по возрастанию момента
 * открытия; параметры своего типа с объявленным таймфреймом; инструмент
 * {@code 11} и идентичность вычисления {@code 22} — различимые числа.
 * Коллабораторов нет: вычислитель конструируется на месте.
 *
 * <p><b>Представитель контракта — вычислитель EMA</b>, кроме клетки
 * `U1.7`, где предмет наблюдения есть перечень <b>всех восьми</b>
 * вычислителей: умолчания интерфейса одни на всех, и кейс на каждого
 * вычислителя был бы восемью записями одного ожидания
 * (.claude/rules/carrier-levels.md).
 *
 * <p><b>Выход здесь — РЯД</b>, поэтому каждая клетка называет три
 * величины: с какого бара ряд начинается, сколько в нём членов и чему
 * равен член. Ряд, сдвинутый на бар, несёт верные числа на неверных
 * отметках времени.
 */
class CalculatorContractTest {

    private static final int PERIOD = 3;

    /** Выведенный прогрев EMA — вдвое больше периода; само число живёт в реализации (M-1). */
    private static final int DERIVED_WARMUP = 2 * PERIOD;

    private static final int BARS = 12;

    private final EmaCalculator calculator = new EmaCalculator();

    /** Override больше выведенного: он и есть эффективный прогрев. */
    @Test
    @DisplayName("U1.1 — override 8 больше выведенного 6: ряд начинается с бара 8, членов 4")
    void u1_1_anExplicitWarmupOverridesTheDerivedOne() {
        assertThat(calculator.effectiveWarmup(8, DERIVED_WARMUP))
                .as("эффективный прогрев равен override")
                .isEqualTo(8);

        List<IndicatorValue> values = calculate(emaParams(PERIOD, 8));

        assertThat(values).hasSize(BARS - 8);
        assertThat(values.getFirst().getCandleTimestamp())
                .as("выведенная граница 6 не применяется")
                .isEqualTo(barAt(8));
    }

    /** Override пуст — работает выведенное. */
    @Test
    @DisplayName("U1.2 — override пуст: ряд начинается с выведенного бара 6, членов 6")
    void u1_2_anAbsentOverrideFallsBackToTheDerivedWarmup() {
        assertThat(calculator.effectiveWarmup(null, DERIVED_WARMUP)).isEqualTo(DERIVED_WARMUP);

        List<IndicatorValue> values = calculate(emaParams(PERIOD, null));

        assertThat(values).hasSize(BARS - DERIVED_WARMUP);
        assertThat(values.getFirst().getCandleTimestamp()).isEqualTo(barAt(DERIVED_WARMUP));
    }

    /** Override меньше выведенного принимается как есть: встречной проверки нет. */
    @Test
    @DisplayName("U1.3 — override 2 меньше выведенного 6: ряд начинается с бара 2, раньше выведенной границы")
    void u1_3_anOverrideBelowTheDerivedWarmupIsTakenAsIs() {
        assertThat(calculator.effectiveWarmup(2, DERIVED_WARMUP)).isEqualTo(2);

        List<IndicatorValue> values = calculate(emaParams(PERIOD, 2));

        assertThat(values).hasSize(BARS - 2);
        assertThat(values.getFirst().getCandleTimestamp())
                .as("граница ряда раньше выведенной: встречной проверки вычислитель не делает")
                .isEqualTo(barAt(2));
    }

    /** Отметка времени значения — момент ОТКРЫТИЯ своего бара в UTC. */
    @Test
    @DisplayName("U1.4 — базовая сборка: отметка времени равна моменту открытия бара, UTC")
    void u1_4_theValueTimestampIsTheOpeningMomentOfItsBarInUtc() {
        List<IndicatorValue> values = calculate(emaParams(PERIOD, null));
        Candle warmupBar = risingSeries(BARS).get(DERIVED_WARMUP);

        OffsetDateTime stamp = values.getFirst().getCandleTimestamp();

        assertThat(stamp.toInstant().toEpochMilli())
                .as("ни закрытие бара, ни момент расчёта: ровно момент открытия")
                .isEqualTo(warmupBar.getOpenTimestamp());
        assertThat(stamp.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    /** Идентичности на результате — только те, что поданы аргументом. */
    @Test
    @DisplayName("U1.5 — базовая сборка: на каждом значении инструмент 11 и идентичность 22, и ничего сверх")
    void u1_5_theIdentitiesOnEachValueAreExactlyTheArgumentsGiven() {
        List<IndicatorValue> values = calculate(emaParams(PERIOD, null));

        assertThat(values).isNotEmpty().allSatisfy(value -> {
            assertThat(value.getInstrumentId()).isEqualTo(INSTRUMENT_ID);
            assertThat(value.getIndicatorConfigId()).isEqualTo(COMPUTATION_ID);
        });
    }

    /** Технический идентификатор и audit-поля ставит персистентность, не вычислитель. */
    @Test
    @DisplayName("U1.6 — базовая сборка: ни идентификатора, ни audit-полей ни на одном значении")
    void u1_6_neitherTheTechnicalIdNorTheAuditFieldsAreSetByTheCalculator() {
        List<IndicatorValue> values = calculate(emaParams(PERIOD, null));

        assertThat(values).isNotEmpty().allSatisfy(value -> {
            assertThat(value.getId()).isNull();
            assertThat(value.getCreatedAt()).isNull();
            assertThat(value.getCreatedBy()).isNull();
            assertThat(value.getModifiedAt()).isNull();
            assertThat(value.getModifiedBy()).isNull();
            assertThat(value.getExternalCreatedAt()).isNull();
            assertThat(value.getExternalModifiedAt()).isNull();
        });
    }

    /** Объявленный тип вычислителя — тот же, что у его значений: по нему его выбирает джоба. */
    @Test
    @DisplayName("U1.7 — все восемь вычислителей: объявленный тип совпадает с типом произведённых значений")
    void u1_7_eachCalculatorDeclaresTheTypeItActuallyProduces() {
        List<Candle> candles = risingSeries(40);

        assertThat(allCalculators()).hasSize(8).allSatisfy(each -> {
            List<IndicatorValue> values =
                    each.calculate(INSTRUMENT_ID, COMPUTATION_ID, candles, paramsFor(each));
            assertThat(values).as("вычислитель %s не произвёл ни одного значения", each.getType()).isNotEmpty();
            assertThat(values).allSatisfy(value -> assertThat(value.getType()).isEqualTo(each.getType()));
        });
    }

    /** Ряд отдан по возрастанию отметки времени, без пропусков внутри. */
    @Test
    @DisplayName("U1.8 — базовая сборка: отметки ряда равны барам 6..11 подряд, пропусков нет")
    void u1_8_theSeriesIsOrderedByTimestampWithoutGaps() {
        List<IndicatorValue> values = calculate(emaParams(PERIOD, null));

        assertThat(values).extracting(IndicatorValue::getCandleTimestamp)
                .as("порядок входа сохранён, пропусков внутри ряда нет")
                .containsExactlyElementsOf(barsAt(DERIVED_WARMUP, BARS - 1));
    }

    /** Прогрев больше длины ряда — ряд пуст, частичных значений нет. */
    @Test
    @DisplayName("U1.9 — эффективный прогрев 50 больше длины 12: ряд пуст, исключения нет")
    void u1_9_aWarmupLongerThanTheHistoryYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(calculate(emaParams(PERIOD, 50))).isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /** Пустой ряд свечей — пустой ряд значений. */
    @Test
    @DisplayName("U1.10 — ряд свечей пуст: ряд пуст, исключения нет")
    void u1_10_anEmptyCandleSeriesYieldsAnEmptySeries() {
        assertThatCode(() -> assertThat(
                calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, List.of(), emaParams(PERIOD, null)))
                .isEmpty())
                .as("исключения нет")
                .doesNotThrowAnyException();
    }

    /** Прогрев нулевой — границу задаёт разгон самой формулы. */
    @Test
    @DisplayName("U1.11 — прогрев 0: ряд начинается с бара 2, где формула определена, членов 10")
    void u1_11_aZeroWarmupLeavesTheBoundaryToTheFormulaItself() {
        List<IndicatorValue> values = calculate(emaParams(PERIOD, 0));

        assertThat(values).hasSize(BARS - (PERIOD - 1));
        assertThat(values.getFirst().getCandleTimestamp())
                .as("граница задаётся не прогревом, а разгоном серии: период минус один")
                .isEqualTo(barAt(PERIOD - 1));
    }

    // --- базовая сборка ----------------------------------------------------

    private List<IndicatorValue> calculate(IndicatorParams params) {
        return calculator.calculate(INSTRUMENT_ID, COMPUTATION_ID, risingSeries(BARS), params);
    }
}
