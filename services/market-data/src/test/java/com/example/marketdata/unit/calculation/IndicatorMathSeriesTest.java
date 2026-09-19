package com.example.marketdata.unit.calculation;

import static com.example.marketdata.unit.calculation.CalcFixture.numbers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketdata.util.IndicatorMath;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Числовые помощники — серия EMA, окно SMA, популяционное отклонение:
 * группа `U2` документа `.claude/tests/cases/market-data-indicators.md`.
 *
 * <p><b>Базовая сборка:</b> ряд чисел и длина окна; утилита статическая,
 * состояния не держит.
 *
 * <p><b>Дома у формул нет</b> (находка M-1): формула сглаживания и вид
 * отклонения живут только в javadoc реализации. Поэтому здесь прогоняемы
 * лишь <b>структурные</b> ожидания серии — длина, положение разгона,
 * поведение на короткой истории — и одно свойство, не зависящее от вида
 * сглаживания. Ожидание, списанное с реализации, зелено по построению и
 * охраняет ноль, поэтому клетки `U2.5`-`U2.7` кода не получили
 * (§«Кейсы, не прогоняемые сегодня»). Той же причиной кода не получила и
 * `U2.9` — негодное окно: у помощников ветвь отказа своя, и дом её не
 * называет ровно так же, как у вычислителей (`U1.12`).
 */
class IndicatorMathSeriesTest {

    /** Серия выровнена по входу: короткая история даёт пустые члены, а не короткий ряд. */
    @Test
    @DisplayName("U2.1 — ряд из 2 чисел короче окна 3: серия длиной 2 и вся пуста, исключения нет")
    void u2_1_aHistoryShorterThanTheWindowYieldsAnAllEmptySeriesOfTheInputLength() {
        assertThatCode(() -> {
            BigDecimal[] series = IndicatorMath.emaSeries(numbers("10", "20"), 3);

            assertThat(series).hasSize(2).containsOnlyNulls();
        }).as("исключения нет").doesNotThrowAnyException();
    }

    /** Ряд длиной ровно в окно — заполнен единственный член, последний. */
    @Test
    @DisplayName("U2.2 — ряд из 3 чисел, окно 3: заполнен только член 2, члены 0 и 1 пусты")
    void u2_2_aHistoryExactlyTheWindowFillsOnlyItsLastMember() {
        BigDecimal[] series = IndicatorMath.emaSeries(numbers("10", "20", "30"), 3);

        assertThat(series).hasSize(3);
        assertThat(series[0]).isNull();
        assertThat(series[1]).isNull();
        assertThat(series[2]).as("разгон кончается на последнем члене окна").isNotNull();
    }

    /** Окно единичное — разгона нет вовсе. */
    @Test
    @DisplayName("U2.3 — окно 1: заполнены все четыре члена серии, пустых нет")
    void u2_3_aUnitWindowLeavesNoWarmupAtAll() {
        BigDecimal[] series = IndicatorMath.emaSeries(numbers("10", "20", "30", "40"), 1);

        assertThat(series).hasSize(4).doesNotContainNull();
    }

    /** Сглаживание постоянного ряда постоянно — свойство, не зависящее от вида сглаживания. */
    @Test
    @DisplayName("U2.4 — ряд из пяти равных 7, окно 3: все заполненные члены равны 7")
    void u2_4_smoothingAConstantSeriesIsConstant() {
        BigDecimal[] series = IndicatorMath.emaSeries(numbers("7", "7", "7", "7", "7"), 3);

        assertThat(series).hasSize(5);
        for (int index = 2; index < series.length; index++) {
            assertThat(series[index]).as("член %s", index).isEqualByComparingTo("7");
        }
    }

    /** Отклонение равных чисел нулевое, и корень из нуля берётся без отказа. */
    @Test
    @DisplayName("U2.8 — окно из трёх равных 7: отклонение равно нулю, отказа нет")
    void u2_8_theDeviationOfEqualNumbersIsZeroAndTheRootDoesNotRefuse() {
        assertThatCode(() -> assertThat(
                IndicatorMath.populationStdDev(numbers("7", "7", "7"), 0, 3, new BigDecimal("7")))
                .isEqualByComparingTo("0"))
                .as("корень из нуля берётся без отказа")
                .doesNotThrowAnyException();
    }
}
