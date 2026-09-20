package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.mapping.OkxResponseConverter;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Конвертер: числовые и знаковые конвенции границы — группа `U5`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Position.md §«Direction mapping»;
 * docs/models/mapping/PositionCloseResult.md §«Знак финансирования
 * нормализуется здесь, и только здесь»).
 *
 * <p><b>Базовая сборка:</b> {@code OkxResponseConverter} собран
 * конструктором; на вход подаётся сырая строка либо доменное значение.
 * Именованные формы зовутся напрямую — то же, что делает порождённая
 * реализация маппера по имени квалификатора.
 *
 * <p>Конвенция знака — свойство <b>источника</b>, и снимается она ровно
 * на границе. Форма, снявшая знак дважды либо не снявшая ни разу, не
 * роняет ничего: она даёт верное по виду число неверного смысла.
 */
class ConverterNumericTest {

    private final OkxResponseConverter converter = new OkxResponseConverter();

    /** Форма конвертера делегирует разбору и своей арифметики не имеет. */
    @Test
    @DisplayName("U5.1 — числовая строка: делегация разбору")
    void u5_1_theDecimalFormDelegates() {
        BigDecimal parsed = converter.okxDecimal("100.5");

        assertThat(parsed).isEqualByComparingTo("100.5");
        assertThat(parsed.scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("U5.2 — эпоха в момент UTC: та же делегация")
    void u5_2_theTimeFormDelegates() {
        OffsetDateTime parsed = converter.okxTimeToOffset("1700000000000");

        assertThat(parsed).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("U5.3 — положительная нога: размер как есть")
    void u5_3_aPositiveLegKeepsItsSize() {
        assertThat(converter.absSize("5")).isEqualByComparingTo("5");
    }

    /** Размер эпизода знака не несёт: знак уходит в направление. */
    @Test
    @DisplayName("U5.4 — отрицательная нога: размер по модулю")
    void u5_4_aNegativeLegGivesTheModulus() {
        assertThat(converter.absSize("-5")).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("U5.5 — нулевая нога: ноль остаётся нулём")
    void u5_5_zeroSizeStaysZero() {
        assertThat(converter.absSize("0")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U5.6 — пустой размер даёт пустоту")
    void u5_6_anEmptySizeIsEmptiness() {
        assertThat(converter.absSize("")).isNull();
        assertThat(converter.absSize(null)).isNull();
    }

    @Test
    @DisplayName("U5.7 — положительная нога даёт длинное направление")
    void u5_7_aPositiveLegIsLong() {
        assertThat(converter.direction("5")).isEqualTo(Position.Direction.LONG);
    }

    @Test
    @DisplayName("U5.8 — отрицательная нога даёт короткое направление")
    void u5_8_aNegativeLegIsShort() {
        assertThat(converter.direction("-5")).isEqualTo(Position.Direction.SHORT);
    }

    /** Подставленное здесь значение стало бы наблюдением, которого не было. */
    @Test
    @DisplayName("U5.9 — нулевая нога направления не имеет")
    void u5_9_aZeroLegHasNoDirection() {
        assertThat(converter.direction("0")).isNull();
    }

    /** Тот же исход, что у нуля, и различает их размер: у пустого входа пуст и он. */
    @Test
    @DisplayName("U5.10 — пустое направление")
    void u5_10_anEmptyLegHasNoDirection() {
        assertThat(converter.direction("")).isNull();
        assertThat(converter.direction(null)).isNull();
        assertThat(converter.absSize("")).isNull();
    }

    /** Признак — знак числа, а не текст. */
    @Test
    @DisplayName("U5.11 — нуль другой записи даёт ту же пустоту")
    void u5_11_zeroOfAnyNotationHasNoDirection() {
        assertThat(converter.direction("0.000")).isNull();
    }

    @Test
    @DisplayName("U5.12 — несобытийное слагаемое: число как есть")
    void u5_12_aNonEventValueIsCarried() {
        assertThat(converter.nonEventDecimal("12.5")).isEqualByComparingTo("12.5");
    }

    /** У слагаемого тождества величина существует всегда. */
    @Test
    @DisplayName("U5.13 — пустая строка слагаемого даёт ноль, а не пустоту")
    void u5_13_anEmptyNonEventValueIsZero() {
        assertThat(converter.nonEventDecimal("")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U5.14 — отсутствие слагаемого даёт ноль")
    void u5_14_anAbsentNonEventValueIsZero() {
        assertThat(converter.nonEventDecimal(null)).isEqualByComparingTo("0");
    }

    /** Ветвь пустоты от ветви нуля по выходу не различима — объявленное свойство конвенции. */
    @Test
    @DisplayName("U5.15 — нулевое слагаемое: вход и выход совпадают")
    void u5_15_zeroInIsZeroOut() {
        assertThat(converter.nonEventDecimal("0")).isEqualByComparingTo("0");
    }

    /** Слагаемое источника знаково, домен хранит уплаченное фондирование издержкой. */
    @Test
    @DisplayName("U5.16 — уплаченное фондирование становится издержкой")
    void u5_16_paidFundingBecomesACost() {
        assertThat(converter.fundingCost("3")).isEqualByComparingTo("-3");
    }

    /** Различимость сохраняется: модуль здесь был бы тихой ошибкой. */
    @Test
    @DisplayName("U5.17 — полученное фондирование становится отрицательной издержкой")
    void u5_17_receivedFundingBecomesANegativeCost() {
        assertThat(converter.fundingCost("-3")).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("U5.18 — пустое фондирование даёт ноль со снятым знаком нуля")
    void u5_18_anEmptyFundingIsZero() {
        assertThat(converter.fundingCost("")).isEqualByComparingTo("0");
    }

    /** Площадка экспоненциальной формы не принимает. */
    @Test
    @DisplayName("U5.19 — сумма запроса пишется плоско")
    void u5_19_anAmountIsWrittenPlain() {
        assertThat(converter.okxAmount(new BigDecimal("0.00000001"))).isEqualTo("0.00000001");
    }

    @Test
    @DisplayName("U5.20 — экспоненциальная запись нормализуется в плоскую")
    void u5_20_anExponentialAmountIsNormalized() {
        assertThat(converter.okxAmount(new BigDecimal("1E-8"))).isEqualTo("0.00000001");
    }

    /** Ключ из тела запроса опускает аннотация формы, а не конвертер (звено `Z3`). */
    @Test
    @DisplayName("U5.21 — пустая сумма даёт пустоту")
    void u5_21_anEmptyAmountIsEmptiness() {
        assertThat(converter.okxAmount(null)).isNull();
    }

    /** Округление под шаг инструмента делает расчётный слой, а не граница. */
    @Test
    @DisplayName("U5.22 — масштаб суммы сохраняется")
    void u5_22_theScaleOfAnAmountIsKept() {
        assertThat(converter.okxAmount(new BigDecimal("1.500"))).isEqualTo("1.500");
    }
}
