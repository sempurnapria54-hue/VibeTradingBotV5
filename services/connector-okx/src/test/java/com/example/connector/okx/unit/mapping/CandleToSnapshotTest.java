package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.mapping.CandleMapper;
import com.example.connector.okx.snapshot.CandleExternalSnapshot;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Свеча → граничный снапшот и фильтр закрытых — группа `U13`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Candle.md §Конвертация и §«Граница:
 * `CandleExternalSnapshot`»).
 *
 * <p><b>Базовая сборка:</b> {@code CandleOkxResponse}, собранный
 * фабрикой из девяти строк; все поля непусты, признак закрытия —
 * закрыта.
 */
class CandleToSnapshotTest {

    private final CandleMapper mapper = Mappers.candle();

    @Test
    @DisplayName("U13.1 — базовая сборка: время эпохой, четыре цены и объём числами")
    void u13_1_theBaseAssemblyLandsFieldByField() {
        CandleExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.candle());

        assertThat(snapshot.getOpenTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(snapshot.getOpen()).isEqualByComparingTo("100");
        assertThat(snapshot.getHigh()).isEqualByComparingTo("110");
        assertThat(snapshot.getLow()).isEqualByComparingTo("90");
        assertThat(snapshot.getClose()).isEqualByComparingTo("105");
        assertThat(snapshot.getVolume()).isEqualByComparingTo("12");
        assertThat(snapshot.getConfirm()).isTrue();
    }

    @Test
    @DisplayName("U13.2 — незакрытая свеча: признак ложен")
    void u13_2_anUnconfirmedCandleIsFalse() {
        assertThat(mapper.integrationToSnapshot(
                OkxFixture.candleWith(OkxFixture.CANDLE_CONFIRM, "0")).getConfirm()).isFalse();
    }

    /** Подставленная истина пустила бы незакрытую свечу в индикаторы. */
    @Test
    @DisplayName("U13.3 — пустой признак закрытия ложен")
    void u13_3_anEmptyConfirmIsFalse() {
        assertThat(mapper.integrationToSnapshot(
                OkxFixture.candleWith(OkxFixture.CANDLE_CONFIRM, "")).getConfirm()).isFalse();
    }

    /** Словарь площадки — один символ: подбор слов завёл бы вторую конвенцию. */
    @Test
    @DisplayName("U13.4 — слово вместо символа признака ложно")
    void u13_4_aWordInsteadOfTheSymbolIsFalse() {
        assertThat(mapper.integrationToSnapshot(
                OkxFixture.candleWith(OkxFixture.CANDLE_CONFIRM, "true")).getConfirm()).isFalse();
    }

    /** Объёмы validation-only за границу не выходят. */
    @Test
    @DisplayName("U13.5 — двух объёмов в составе снапшота нет")
    void u13_5_twoVolumesDoNotCrossTheBoundary() {
        assertThat(CandleExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("volumeCurrency", "volumeCurrencyQuote");
    }

    @Test
    @DisplayName("U13.6 — пустая цена открытия: прочие цены не затронуты")
    void u13_6_anEmptyOpenPriceLeavesTheOthers() {
        CandleExternalSnapshot snapshot =
                mapper.integrationToSnapshot(OkxFixture.candleWith(OkxFixture.CANDLE_OPEN, ""));

        assertThat(snapshot.getOpen()).isNull();
        assertThat(snapshot.getHigh()).isEqualByComparingTo("110");
        assertThat(snapshot.getLow()).isEqualByComparingTo("90");
        assertThat(snapshot.getClose()).isEqualByComparingTo("105");
    }

    /** У свечи без времени открытия координаты нет, и ноль означал бы эпоху. */
    @Test
    @DisplayName("U13.7 — пустое время открытия даёт пустоту")
    void u13_7_anEmptyOpenTimeIsEmptiness() {
        assertThat(mapper.integrationToSnapshot(
                OkxFixture.candleWith(OkxFixture.CANDLE_TS, "")).getOpenTimestamp()).isNull();
    }

    @Test
    @DisplayName("U13.8 — неразбираемое время открытия отказывает")
    void u13_8_anUnparseableOpenTimeRefuses() {
        var candle = OkxFixture.candleWith(OkxFixture.CANDLE_TS, "abc");

        assertThatThrownBy(() -> mapper.integrationToSnapshot(candle))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("U13.9 — нулевой объём есть наблюдённый ноль, не пустота")
    void u13_9_aZeroVolumeIsObservedZero() {
        assertThat(mapper.integrationToSnapshot(
                OkxFixture.candleWith(OkxFixture.CANDLE_VOLUME, "0")).getVolume())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U13.10 — пустота вместо формы источника даёт пустоту")
    void u13_10_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot(null)).isNull();
    }
}
