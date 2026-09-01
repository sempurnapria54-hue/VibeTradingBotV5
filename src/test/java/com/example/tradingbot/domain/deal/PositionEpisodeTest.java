package com.example.tradingbot.domain.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает доменные предикаты эпизода позиции с их домом
 * (docs/models/domain/core/Position.md §«Адресуемая единица эпизода —
 * пара, а не идентификатор», docs/models/domain/aggregate/Deal.md
 * §«Окно линковки движений»).
 *
 * <p>Несущее для этого теста — ОДНОСТОРОННОСТЬ прежней формулировки:
 * «другой идентификатор ⇒ новый эпизод» верно и сегодня, ошибочной была
 * ОБРАТНАЯ импликация. Источник переиспользует идентификатор у
 * переоткрытой позиции, поэтому пара обязана разделять то, что он
 * склеивает.
 */
class PositionEpisodeTest {

    private static final OffsetDateTime FIRST = OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SECOND = OffsetDateTime.of(2026, 9, 1, 11, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("Переиспользованный идентификатор с другим временем создания — ДРУГОЙ эпизод")
    void reusedIdentifierWithOtherCreationTimeIsAnotherEpisode() {
        Position episode = episode("pos-1", FIRST, Position.Status.ACTIVE);

        assertTrue(episode.sameEpisode("pos-1", FIRST));
        assertFalse(episode.sameEpisode("pos-1", SECOND));
        assertFalse(episode.sameEpisode("pos-2", FIRST));
    }

    @Test
    @DisplayName("Предикат «запись закрытия добыта» стои́т на непустоте готового net'а")
    void closeRecordFetchedReadsNet() {
        Position episode = episode("pos-1", FIRST, Position.Status.CLOSED);
        assertFalse(episode.closeRecordFetched());
        assertTrue(episode.awaitsCloseRecord());

        episode.setExternalRealizedProfit(BigDecimal.valueOf(-12.5));
        assertTrue(episode.closeRecordFetched());
        assertFalse(episode.awaitsCloseRecord());
    }

    @Test
    @DisplayName("Живой строки эпизода ждать положения закрытия не полагается")
    void liveEpisodeDoesNotAwaitCloseRecord() {
        assertFalse(episode("pos-1", FIRST, Position.Status.ACTIVE).awaitsCloseRecord());
    }

    @Test
    @DisplayName("Живой эпизод сделки один: закрытые остаются строками той же таблицы")
    void livePositionPicksTheActiveEpisode() {
        Position closed = episode("pos-1", FIRST, Position.Status.CLOSED);
        Position live = episode("pos-1", SECOND, Position.Status.ACTIVE);
        live.setExternalSize(BigDecimal.ONE);
        Deal deal = new Deal();
        deal.setPositions(List.of(closed, live));

        assertSame(live, deal.livePosition());
        assertTrue(deal.hasLivePositionRisk());
        assertEquals(List.of(closed), deal.episodesAwaitingCloseRecord());
    }

    @Test
    @DisplayName("Эпизодов нет вовсе: живого нет, живого риска нет, отказа вычисления нет")
    void noEpisodesAtAll() {
        Deal deal = new Deal();

        assertNull(deal.livePosition());
        assertFalse(deal.hasLivePositionRisk());
        assertTrue(deal.episodesAwaitingCloseRecord().isEmpty());
    }

    @Test
    @DisplayName("Признак наблюдения позиции: два дизъюнкта, каждый несущий по отдельности")
    void positionObservedHasTwoIndependentDisjuncts() {
        Deal byRecovery = new Deal();
        byRecovery.setEntryReason(Deal.EntryReason.RECOVERY);
        assertTrue(byRecovery.positionObserved());

        Deal byFill = new Deal();
        byFill.setEntryReason(Deal.EntryReason.STRATEGY);
        DealTranche filled = new DealTranche();
        filled.setEntryFilled(BigDecimal.valueOf(0.02));
        byFill.setTranches(List.of(filled));
        assertTrue(byFill.positionObserved());

        Deal beforeFill = new Deal();
        beforeFill.setEntryReason(Deal.EntryReason.STRATEGY);
        DealTranche empty = new DealTranche();
        beforeFill.setTranches(List.of(empty));
        assertFalse(beforeFill.positionObserved());
    }

    @Test
    @DisplayName("Порог доказанного покрытия двигается только вперёд")
    void coverageProvenThroughIsMonotonic() {
        Deal deal = new Deal();

        deal.advanceCoverageProvenThrough(SECOND);
        assertEquals(SECOND, deal.getCoverageProvenThrough());

        deal.advanceCoverageProvenThrough(FIRST);
        assertEquals(SECOND, deal.getCoverageProvenThrough());

        deal.advanceCoverageProvenThrough(null);
        assertEquals(SECOND, deal.getCoverageProvenThrough());
    }

    private Position episode(String externalId, OffsetDateTime createdAt, Position.Status status) {
        Position episode = new Position();
        episode.setExternalId(externalId);
        episode.setExternalCreatedAt(createdAt);
        episode.setStatus(status);
        return episode;
    }
}
