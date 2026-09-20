package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.util.OkxParse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Разбор сырой строки: пять форм и три класса входа — группа `U4`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Order.md §«Конвертация (OKX)»;
 * docs/rules/absent-value-semantics.md).
 *
 * <p><b>Базовая сборка:</b> строка, какой её отдаёт площадка,
 * подаётся в соответствующий метод {@code OkxParse}. Коллабораторов
 * нет, состояния нет.
 *
 * <p>Классов входа три, и они названы у каждой формы: значащая
 * строка, пустая строка, отсутствие значения. Четвёртый — строка,
 * числом не являющаяся, — <b>отказывает</b>, и это объявленный
 * контракт границы, а не оговорка.
 */
class OkxParseTest {

    /** Масштаб берётся из строки и сохраняется. */
    @Test
    @DisplayName("U4.1 — числовая строка в число с сохранённым масштабом")
    void u4_1_aNumericStringKeepsItsScale() {
        BigDecimal parsed = OkxParse.decimal("100.5");

        assertThat(parsed).isEqualByComparingTo("100.5");
        assertThat(parsed.scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("U4.2 — пустая строка числа даёт пустоту")
    void u4_2_anEmptyStringIsEmptiness() {
        assertThat(OkxParse.decimal("")).isNull();
    }

    /** Признак — «пусто или только пробелы», а не длина строки. */
    @Test
    @DisplayName("U4.3 — строка из пробелов даёт пустоту")
    void u4_3_aBlankStringIsEmptiness() {
        assertThat(OkxParse.decimal("   ")).isNull();
    }

    @Test
    @DisplayName("U4.4 — отсутствие значения даёт пустоту")
    void u4_4_anAbsentValueIsEmptiness() {
        assertThat(OkxParse.decimal(null)).isNull();
    }

    /** Тихая пустота увела бы нарушение контракта источника в «факт не добыт». */
    @Test
    @DisplayName("U4.5 — неразбираемое число отказывает, а не пустеет")
    void u4_5_anUnparseableNumberRefuses() {
        assertThatThrownBy(() -> OkxParse.decimal("abc")).isInstanceOf(NumberFormatException.class);
    }

    /** Знаковые конвенции живут выше, в конвертере. */
    @Test
    @DisplayName("U4.6 — отрицательное значение переносится как есть")
    void u4_6_aNegativeValueIsCarriedAsIs() {
        assertThat(OkxParse.decimal("-0.0005")).isEqualByComparingTo("-0.0005");
    }

    @Test
    @DisplayName("U4.7 — экспоненциальная запись разбирается")
    void u4_7_anExponentialNotationIsParsed() {
        assertThat(OkxParse.decimal("1E-8")).isEqualByComparingTo(new BigDecimal("1E-8"));
    }

    /** Время у нас UTC сквозным правилом (docs/rules/time-utc.md). */
    @Test
    @DisplayName("U4.8 — эпоха в миллисекундах в момент с нулевым смещением")
    void u4_8_anEpochBecomesAUtcMoment() {
        OffsetDateTime parsed = OkxParse.offsetTime("1700000000000");

        assertThat(parsed).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("U4.9 — пустая строка и отсутствие значения у момента дают пустоту")
    void u4_9_anEmptyMomentIsEmptiness() {
        assertThat(OkxParse.offsetTime("")).isNull();
        assertThat(OkxParse.offsetTime(null)).isNull();
    }

    /** Ноль — значащее число, и пустоту от него отделяет только пустая строка. */
    @Test
    @DisplayName("U4.10 — ноль эпохи даёт момент эпохи, а не пустоту")
    void u4_10_zeroIsTheEpochMomentNotEmptiness() {
        assertThat(OkxParse.offsetTime("0")).isEqualTo(OffsetDateTime.parse("1970-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("U4.11 — нечисловая строка момента отказывает")
    void u4_11_anUnparseableMomentRefuses() {
        assertThatThrownBy(() -> OkxParse.offsetTime("нечисло")).isInstanceOf(NumberFormatException.class);
    }

    /** Форма нужна там, где хранится эпоха: снапшот свечи, снапшот книги. */
    @Test
    @DisplayName("U4.12 — эпоха без перевода в момент")
    void u4_12_anEpochStaysANumber() {
        assertThat(OkxParse.epochMillis("1700000000000")).isEqualTo(1_700_000_000_000L);
    }

    @Test
    @DisplayName("U4.13 — пустая строка и отсутствие значения у эпохи дают пустоту")
    void u4_13_anEmptyEpochIsEmptiness() {
        assertThat(OkxParse.epochMillis("")).isNull();
        assertThat(OkxParse.epochMillis(null)).isNull();
    }

    @Test
    @DisplayName("U4.14 — целое число заявок уровня книги")
    void u4_14_anIntegerIsParsed() {
        assertThat(OkxParse.integer("3")).isEqualTo(3);
    }

    @Test
    @DisplayName("U4.15 — пустая строка и отсутствие значения у целого дают пустоту")
    void u4_15_anEmptyIntegerIsEmptiness() {
        assertThat(OkxParse.integer("")).isNull();
        assertThat(OkxParse.integer(null)).isNull();
    }

    /** Целое разбирается строго: дробная запись числом заявок не бывает. */
    @Test
    @DisplayName("U4.16 — дробная запись целого отказывает")
    void u4_16_aFractionalIntegerRefuses() {
        assertThatThrownBy(() -> OkxParse.integer("3.0")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("U4.17 — момент без смещения: время срабатывания условной заявки")
    void u4_17_anInstantIsParsed() {
        assertThat(OkxParse.instant("1700000000000")).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
    }

    /** У живой защиты времени срабатывания нет — пустота здесь штатный исход. */
    @Test
    @DisplayName("U4.18 — пустая строка и отсутствие значения у момента без смещения дают пустоту")
    void u4_18_anEmptyInstantIsEmptiness() {
        assertThat(OkxParse.instant("")).isNull();
        assertThat(OkxParse.instant(null)).isNull();
    }

    @Test
    @DisplayName("U4.19 — ведущие нули разбор не роняют ни у одной из пяти форм")
    void u4_19_leadingZeroesDoNotBreakAnyForm() {
        assertThat(OkxParse.decimal("00100")).isEqualByComparingTo("100");
        assertThat(OkxParse.decimal("00100").scale()).isZero();
        assertThat(OkxParse.epochMillis("00100")).isEqualTo(100L);
        assertThat(OkxParse.integer("00100")).isEqualTo(100);
        assertThat(OkxParse.offsetTime("00100")).isEqualTo(OffsetDateTime.parse("1970-01-01T00:00:00.100Z"));
        assertThat(OkxParse.instant("00100")).isEqualTo(Instant.ofEpochMilli(100L));
    }
}
