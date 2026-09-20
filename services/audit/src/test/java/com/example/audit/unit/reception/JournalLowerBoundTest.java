package com.example.audit.unit.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.audit.domain.service.JournalCompletenessService;
import com.example.audit.persistence.service.JournalCompletenessSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Нижняя граница полноты журнала: два операнда и обе ветви пустоты —
 * группа `U1` документа `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Группа своя у дерева, и это объявленное расхождение копий:</b> у
 * журнала операндов границы два — позднейший момент наблюдения и самый
 * ранний момент приёма сохранившейся строки, — а у статистики один
 * (docs/models/domain/other/StatisticsFact.md §«Состояние приёма и
 * полнота чисел статистики»). Общее живёт контрактом
 * {@code ReceptionCompletenessContract}.
 *
 * <p><b>Области у двух операндов разные намеренно:</b> пустая ветвь
 * решается ПОДПИСАННЫМИ парами, а максимум считается по ВСЕМ строкам. Что
 * сам максимум посчитан по всем строкам — предмет запроса, и мерит его
 * база; свёртка отдаёт полученное как получено (`U1.7`).
 */
class JournalLowerBoundTest {

    private static final String GROUP = "audit-journal";

    private static final OffsetDateTime OBSERVED =
            OffsetDateTime.of(2026, 9, 10, 3, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime RECORDED =
            OffsetDateTime.of(2026, 9, 9, 3, 0, 0, 0, ZoneOffset.UTC);

    private final JournalCompletenessService service = new JournalCompletenessService();

    @Test
    @DisplayName("U1.1 — момент наблюдения позже самого раннего приёма: границей служит он")
    void u1_1_theLaterObservationWinsOverTheEarlierRecord() {
        assertThat(lowerBound(2L, OBSERVED, RECORDED))
                .as("границей отдаётся позднейший из двух")
                .contains(OBSERVED);
    }

    @Test
    @DisplayName("U1.2 — самый ранний приём позже момента наблюдения: границей служит он")
    void u1_2_theLaterRecordWinsOverTheEarlierObservation() {
        assertThat(lowerBound(2L, RECORDED, OBSERVED)).contains(OBSERVED);
    }

    @Test
    @DisplayName("U1.3 — оба момента равны: граница равна этому моменту")
    void u1_3_equalOperandsLeaveNoFork() {
        assertThat(lowerBound(1L, OBSERVED, OBSERVED))
                .as("исход не зависит от того, какая ветвь сравнения сработала")
                .contains(OBSERVED);
    }

    @Test
    @DisplayName("U1.4 — журнал пуст: границей служит момент наблюдения, отказа нет")
    void u1_4_anEmptyJournalFallsBackToTheObservation() {
        assertThat(lowerBound(1L, OBSERVED, null))
                .as("ряд следствий пуст — обещать по нему нечего, но наблюдение уже идёт")
                .contains(OBSERVED);
    }

    @Test
    @DisplayName("U1.5 — подписанных пар ноль: границы НЕТ, о моментах не спрошено")
    void u1_5_anEmptyDomainYieldsNoBoundAtAll() {
        JournalCompletenessSource source = source(0L, OBSERVED, RECORDED);

        assertThat(service.lowerBound(source, GROUP))
                .as("отсутствие есть значение, а не ноль")
                .isEmpty();
        verify(source, never()).latestObservedSince(anyString());
        verify(source, never()).earliestRecordedAt();
    }

    @Test
    @DisplayName("U1.6 — строки есть, но все сняты с подписки: границы НЕТ")
    void u1_6_unsubscribedRowsPromiseNothing() {
        assertThat(lowerBound(0L, OBSERVED, RECORDED))
                .as("минимум по журналу обещал бы полноту, которой никто не мерил")
                .isEmpty();
    }

    @Test
    @DisplayName("U1.7 — максимум наблюдения принадлежит снятой строке: отдаётся как получен")
    void u1_7_theFoldDoesNotFilterRowsOfItsOwn() {
        assertThat(lowerBound(1L, OBSERVED, RECORDED))
                .as("область максимума — все строки состояния, и мерит её запрос, а не свёртка")
                .contains(OBSERVED);
    }

    @Test
    @DisplayName("U1.8 — тот же вход при нулевой подписке: границы нет")
    void u1_8_emptinessIsDecidedBySubscriptionAndNotByTheMaximum() {
        assertThat(lowerBound(0L, OBSERVED, RECORDED))
                .as("две области намеренно разные: пустоту решает подписка")
                .isEmpty();
    }

    @Test
    @DisplayName("U1.9 — два вызова подряд дают один ответ: состояния свёртка не держит")
    void u1_9_theFoldKeepsNoStateBetweenCalls() {
        JournalCompletenessSource source = source(2L, OBSERVED, RECORDED);

        assertThat(service.lowerBound(source, GROUP)).isEqualTo(service.lowerBound(source, GROUP));
    }

    @Test
    @DisplayName("U1.10 — обе ветви пустоты отвечают ОДНИМ значением")
    void u1_10_bothEmptinessBranchesAnswerAlike() {
        assertThat(lowerBound(0L, OBSERVED, RECORDED))
                .as("«строк нет вовсе» и «все сняты с подписки» неразличимы по ответу")
                .isEqualTo(lowerBound(0L, null, null));
    }

    // --- оснастка ---------------------------------------------------------

    private Optional<OffsetDateTime> lowerBound(Long subscribedPairs, OffsetDateTime observed,
                                                OffsetDateTime recorded) {
        return service.lowerBound(source(subscribedPairs, observed, recorded), GROUP);
    }

    private JournalCompletenessSource source(Long subscribedPairs, OffsetDateTime observed,
                                             OffsetDateTime recorded) {
        JournalCompletenessSource source = mock(JournalCompletenessSource.class);
        when(source.countSubscribedPairs(GROUP)).thenReturn(subscribedPairs);
        when(source.latestObservedSince(GROUP)).thenReturn(observed);
        when(source.earliestRecordedAt()).thenReturn(recorded);
        return source;
    }
}
