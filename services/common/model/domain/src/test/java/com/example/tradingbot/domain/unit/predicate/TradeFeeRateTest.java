package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.other.TradeFeeRate;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ставка комиссии: тождество группы и тождество значения — группа `U14`
 * документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/models/domain/other/TradeFeeRate.md §«Масштаб — группа, а не
 * инструмент» и §«Запись и история»).
 *
 * <p><b>Базовая сборка:</b> строка ставки с сырыми строковыми
 * значениями: тип инструмента, идентификатор группы, ставки тейкера и
 * мейкера.
 */
class TradeFeeRateTest {

    @Test
    @DisplayName("U14.1 — тот же тип инструмента и тот же идентификатор группы")
    void u14_1_thePairIdentifiesTheSameGroup() {
        assertThat(rate("SWAP", "1", "0.0005", "0.0002").sameGroupAs("SWAP", "1")).isTrue();
    }

    /** Ключ — пара. */
    @Test
    @DisplayName("U14.2 — совпал только тип инструмента")
    void u14_2_theTypeAloneIsNotTheGroup() {
        assertThat(rate("SWAP", "1", "0.0005", "0.0002").sameGroupAs("SWAP", "2")).isFalse();
    }

    @Test
    @DisplayName("U14.3 — совпал только идентификатор группы")
    void u14_3_theGroupIdAloneIsNotTheGroup() {
        assertThat(rate("SWAP", "1", "0.0005", "0.0002").sameGroupAs("FUTURES", "1")).isFalse();
    }

    @Test
    @DisplayName("U14.4 — наблюдённые ставки записаны той же строкой")
    void u14_4_identicalStringsAreTheSameValue() {
        assertThat(rate("SWAP", "1", "0.0005", "0.0002").sameValueAs("0.0005", "0.0002")).isTrue();
    }

    /** Предикат нормализует запись числа перед сравнением. */
    @Test
    @DisplayName("U14.5 — ставки эквивалентны численно, но записаны иначе")
    void u14_5_numericallyEqualStringsAreTheSameValue() {
        assertThat(rate("SWAP", "1", "0.0005", "0.0002").sameValueAs("0.00050", " 0.0002 ")).isTrue();
    }

    @Test
    @DisplayName("U14.6 — наблюдённая ставка тейкера отличается численно")
    void u14_6_aDifferentTakerRateIsANewValue() {
        assertThat(rate("SWAP", "1", "0.0005", "0.0002").sameValueAs("0.0006", "0.0002")).isFalse();
    }

    /** Пустое отождествляется с пустым. */
    @Test
    @DisplayName("U14.7 — обе стороны сравнения нечисловые либо пустые")
    void u14_7_emptinessEqualsEmptiness() {
        assertThat(rate("SWAP", "1", null, "n/a").sameValueAs("  ", "also-not-a-number")).isTrue();
    }

    @Test
    @DisplayName("U14.8 — одна сторона пуста, вторая — число")
    void u14_8_emptinessDoesNotEqualANumber() {
        assertThat(rate("SWAP", "1", null, "0.0002").sameValueAs("0.0005", "0.0002")).isFalse();
    }

    @Test
    @DisplayName("U14.9 — ставка комиссии-тейкера резолвится числом")
    void u14_9_aNumericTakerRateIsPresent() {
        TradeFeeRate subject = rate("SWAP", "1", "0.0005", "0.0002");

        assertThat(subject.hasTakerFeeRate()).isTrue();
        assertThat(subject.takerFeeRate()).isEqualByComparingTo("0.0005");
    }

    /** Подставленное число ошибалось бы в разрешающую сторону. */
    @Test
    @DisplayName("U14.10 — ставка комиссии-тейкера пуста либо нечисловая")
    void u14_10_anUnresolvableTakerRateIsAbsent() {
        assertThat(rate("SWAP", "1", null, "0.0002").hasTakerFeeRate()).isFalse();
        assertThat(rate("SWAP", "1", null, "0.0002").takerFeeRate()).isNull();
        assertThat(rate("SWAP", "1", "n/a", "0.0002").hasTakerFeeRate()).isFalse();
        assertThat(rate("SWAP", "1", "n/a", "0.0002").takerFeeRate()).isNull();
    }

    /**
     * Аксессор тот же, а признака наличия у мейкерской стороны нет — её
     * читают сравнением, а не предикатом.
     */
    @Test
    @DisplayName("U14.11 — ставка комиссии-мейкера числом, пустая и нечисловая — порознь")
    void u14_11_theMakerSideHasAnAccessorButNoPredicate() {
        assertThat(rate("SWAP", "1", "0.0005", "0.0002").makerFeeRate()).isEqualByComparingTo("0.0002");
        assertThat(rate("SWAP", "1", "0.0005", null).makerFeeRate()).isNull();
        assertThat(rate("SWAP", "1", "0.0005", "n/a").makerFeeRate()).isNull();
        assertThat(Stream.of(TradeFeeRate.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("hasMakerFeeRate");
    }

    private static TradeFeeRate rate(String instrumentType, String feeGroupId,
                                     String takerFeeRate, String makerFeeRate) {
        TradeFeeRate rate = new TradeFeeRate();
        rate.setExternalInstrumentType(instrumentType);
        rate.setExternalFeeGroupId(feeGroupId);
        rate.setExternalTakerFeeRate(takerFeeRate);
        rate.setExternalMakerFeeRate(makerFeeRate);
        return rate;
    }
}
