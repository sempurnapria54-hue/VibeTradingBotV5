package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.at;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.episode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.core.position.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Позиция-эпизод: живой риск, запись закрытия, тождество пары — группа
 * `U12` документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/models/domain/core/Position.md §«Живой риск» и §«Адресуемая
 * единица эпизода — пара, а не идентификатор»;
 * docs/spec/protection-coverage.json, {@code hasLiveEpisode}).
 *
 * <p><b>Базовая сборка:</b> эпизод со статусом, нетто-размером, биржевым
 * идентификатором, биржевым моментом создания и готовым
 * нетто-результатом.
 */
class PositionEpisodeTest {

    @Test
    @DisplayName("U12.1 — активен, размер положителен")
    void u12_1_anActiveSizedEpisodeBearsRisk() {
        assertThat(episode(Position.Status.ACTIVE, "5", null).hasLiveRisk()).isTrue();
    }

    /** Запись есть, риска нет. */
    @Test
    @DisplayName("U12.2 — активен, размер нулевой")
    void u12_2_aZeroSizeBearsNoRisk() {
        assertThat(episode(Position.Status.ACTIVE, "0", null).hasLiveRisk()).isFalse();
    }

    @Test
    @DisplayName("U12.3 — активен, размер пуст")
    void u12_3_anAbsentSizeBearsNoRisk() {
        Position subject = episode(Position.Status.ACTIVE, null, null);

        assertThatCode(subject::hasLiveRisk).doesNotThrowAnyException();
        assertThat(subject.hasLiveRisk()).isFalse();
    }

    @Test
    @DisplayName("U12.4 — закрыт, размер положителен")
    void u12_4_aClosedEpisodeBearsNoRisk() {
        assertThat(episode(Position.Status.CLOSED, "5", null).hasLiveRisk()).isFalse();
    }

    @Test
    @DisplayName("U12.5 — ошибочное состояние, размер положителен")
    void u12_5_anErroredEpisodeBearsNoRisk() {
        assertThat(episode(Position.Status.ERROR, "5", null).hasLiveRisk()).isFalse();
    }

    @Test
    @DisplayName("U12.6 — закрыт")
    void u12_6_closedIsClosed() {
        assertThat(episode(Position.Status.CLOSED, "0", null).isClosed()).isTrue();
    }

    @Test
    @DisplayName("U12.7 — закрыт, нетто-результат заполнен")
    void u12_7_aFetchedCloseRecordEndsTheWait() {
        Position subject = episode(Position.Status.CLOSED, "0", "12");

        assertThat(subject.closeRecordFetched()).isTrue();
        assertThat(subject.awaitsCloseRecord()).isFalse();
    }

    @Test
    @DisplayName("U12.8 — закрыт, нетто-результата нет")
    void u12_8_aClosedEpisodeWithoutRecordWaits() {
        Position subject = episode(Position.Status.CLOSED, "0", null);

        assertThat(subject.closeRecordFetched()).isFalse();
        assertThat(subject.awaitsCloseRecord()).isTrue();
    }

    /** Предикат ожидания требует закрытости. */
    @Test
    @DisplayName("U12.9 — активен, нетто-результата нет")
    void u12_9_anActiveEpisodeDoesNotWait() {
        Position subject = episode(Position.Status.ACTIVE, "5", null);

        assertThat(subject.closeRecordFetched()).isFalse();
        assertThat(subject.awaitsCloseRecord()).isFalse();
    }

    @Test
    @DisplayName("U12.10 — тот же идентификатор и тот же момент создания")
    void u12_10_thePairIdentifiesTheSameEpisode() {
        assertThat(identified("pos-1", at(0)).sameEpisode("pos-1", at(0))).isTrue();
    }

    /** Именно эту склейку пара и чинит. */
    @Test
    @DisplayName("U12.11 — тот же идентификатор, другой момент создания")
    void u12_11_aDifferentMomentIsADifferentEpisode() {
        assertThat(identified("pos-1", at(0)).sameEpisode("pos-1", at(5))).isFalse();
    }

    @Test
    @DisplayName("U12.12 — другой идентификатор, тот же момент")
    void u12_12_aDifferentIdentifierIsADifferentEpisode() {
        assertThat(identified("pos-1", at(0)).sameEpisode("pos-2", at(0))).isFalse();
    }

    /** Названное ограничение предиката, а не ожидание домена. */
    @Test
    @DisplayName("U12.13 — оба операнда наблюдения пусты при пустых полях строки")
    void u12_13_emptinessIsIdentifiedWithEmptiness() {
        assertThat(identified(null, null).sameEpisode(null, null)).isTrue();
    }

    private static Position identified(String externalId, java.time.OffsetDateTime createdAt) {
        Position episode = episode(Position.Status.ACTIVE, "5", null);
        episode.setExternalId(externalId);
        episode.setExternalCreatedAt(createdAt);
        return episode;
    }
}
