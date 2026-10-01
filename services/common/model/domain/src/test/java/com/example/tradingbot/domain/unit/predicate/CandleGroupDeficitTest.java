package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Недостача ряда свечей до плотности — клетки `U18.29`-`U18.32` группы
 * `U18` документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/lifecycles/CandleGroup.md §«Докачка дыр (`REPAIR`)»: недостача —
 * ожидаемое по density-инварианту минус {@code count}).
 *
 * <p><b>Своим классом, а не методами {@code MarketModelsTest}:</b> величина
 * заведена на модели переносом из цикла загрузки, где жила приватным
 * хелпером, и её клетки пишутся отдельно от клеток плотности.
 *
 * <p><b>Базовая сборка:</b> группа свечей с таймфреймом, фактическими
 * границами и поддерживаемым счётчиком.
 */
class CandleGroupDeficitTest {

    private static final long MINUTE = TimeFrame.ONE_MINUTE.getDurationMillis();

    @Test
    @DisplayName("U18.29 — счётчик меньше ожидаемого")
    void u18_29_theDeficitIsTheExpectedCountMinusTheCount() {
        assertThat(group(0L, 3 * MINUTE, 1L).deficit()).isEqualTo(3L);
    }

    @Test
    @DisplayName("U18.30 — счётчик равен ожидаемому")
    void u18_30_aDenseSeriesHasNoDeficit() {
        assertThat(group(0L, 3 * MINUTE, 4L).deficit()).isZero();
    }

    /** Пустое читается нулём. */
    @Test
    @DisplayName("U18.31 — счётчик пуст при непустых границах")
    void u18_31_anAbsentCountReadsAsZero() {
        assertThat(group(0L, 3 * MINUTE, null).deficit()).isEqualTo(4L);
    }

    @Test
    @DisplayName("U18.32 — границы и счётчик пусты")
    void u18_32_anEmptyGroupHasNoDeficit() {
        assertThatCode(() -> group(null, null, null).deficit()).doesNotThrowAnyException();
        assertThat(group(null, null, null).deficit()).isZero();
    }

    private static CandleGroup group(Long first, Long last, Long count) {
        CandleGroup group = new CandleGroup();
        group.setTimeframe(TimeFrame.ONE_MINUTE);
        group.setActualFirstUtcMillis(first);
        group.setActualLastUtcMillis(last);
        group.setCount(count);
        return group;
    }
}
