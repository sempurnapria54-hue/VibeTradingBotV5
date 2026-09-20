package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Экспозиция транша: три собственных слагаемых и четвёртое приписанное —
 * группа `U1` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/protection-coverage.json, величины
 * {@code trancheGrossExposure}, {@code trancheExposure},
 * {@code trancheCloseAttributed}).
 *
 * <p><b>Базовая сборка:</b> транш с наливами — входным, reduce-only и
 * закрытым защитой — своими полями; приписанное закрывающее исполнение
 * уровня сделки своим. Заявок в группе нет: экспозиция считается из полей
 * транша, а не из его коллекций.
 */
class TrancheExposureTest {

    @Test
    @DisplayName("U1.1 — налив входа положителен, прочие три слагаемых пусты")
    void u1_1_grossAndNetEqualTheEntryFill() {
        DealTranche subject = tranche("10", null, null, null);

        assertThat(subject.grossExposure()).isEqualByComparingTo("10");
        assertThat(subject.exposure()).isEqualByComparingTo("10");
        assertThat(subject.hasEntryFill()).isTrue();
    }

    /** Порядок вычитания на результат не влияет — оба слагаемых вычитаются. */
    @Test
    @DisplayName("U1.2 — налив входа, reduce-only и закрытый защитой объём")
    void u1_2_bothReducersAreSubtracted() {
        DealTranche subject = tranche("10", "3", "2", null);

        assertThat(subject.grossExposure()).isEqualByComparingTo("5");
        assertThat(subject.grossExposure())
                .isEqualByComparingTo(dec("10").subtract(dec("2")).subtract(dec("3")));
    }

    /** Брутто и нетто — различные величины, и обе наблюдаемы. */
    @Test
    @DisplayName("U1.3 — те же три плюс приписанное закрывающее исполнение")
    void u1_3_attributedCloseMovesOnlyTheNetExposure() {
        DealTranche subject = tranche("10", "3", "2", "1");

        assertThat(subject.grossExposure()).isEqualByComparingTo("5");
        assertThat(subject.exposure()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("U1.4 — все четыре слагаемых пусты")
    void u1_4_absentAddendsReadAsZero() {
        DealTranche subject = tranche(null, null, null, null);

        assertThatCode(subject::exposure).doesNotThrowAnyException();
        assertThat(subject.grossExposure()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(subject.exposure()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Отсечка объявлена у ЧЕТВЁРТОГО слагаемого ({@code
     * trancheCloseAttributed}), а не у этих трёх: модель отдаёт
     * наблюдённое.
     */
    @Test
    @DisplayName("U1.5 — reduce-only больше входного налива")
    void u1_5_negativeExposureIsNotClamped() {
        DealTranche subject = tranche("1", "5", null, null);

        assertThat(subject.exposure().signum()).isNegative();
        assertThat(subject.exposure()).isEqualByComparingTo("-4");
    }

    @Test
    @DisplayName("U1.6 — налив входа пуст, приписанное положительно")
    void u1_6_attributedWithoutEntryGivesNegativeExposure() {
        DealTranche subject = tranche(null, null, null, "3");

        assertThat(subject.exposure()).isEqualByComparingTo("-3");
        assertThat(subject.hasEntryFill()).isFalse();
    }

    /** Признак строго положительный, а не «непустой». */
    @Test
    @DisplayName("U1.7 — налив входа равен нулю ровно")
    void u1_7_zeroEntryFillIsNotAnEntry() {
        assertThat(tranche("0", null, null, null).hasEntryFill()).isFalse();
    }

    /** От нулевого налива отличается провенансом, не ответом. */
    @Test
    @DisplayName("U1.8 — налив входа пуст (значения нет вовсе)")
    void u1_8_absentEntryFillIsNotAnEntry() {
        assertThat(tranche(null, null, null, null).hasEntryFill()).isFalse();
    }
}
