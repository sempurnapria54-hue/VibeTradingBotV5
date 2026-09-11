package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import com.example.auditstatistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Предикат непрерывности журнала: два конъюнкта, и второй несущий
 * (docs/spec/audit-journal.json, {@code continuityClaimable}).
 *
 * <p><b>Область квантора обязана быть непустой.</b> Свёртка «все» по
 * пустой коллекции истинна, и без сравнения с нулём группа, не
 * наблюдающая ни одной темы, отвечала бы «дыры нет» — ошибка в
 * разрешающую сторону и неразличение «не проверяли» с «проверили, всё в
 * порядке» (docs/concept.md, П1).
 *
 * <p><b>Три ветви самого разрыва здесь не разводятся, и это названо.</b>
 * Они живут предикатом запроса — приём остановлен, момент разрыва
 * записан, строка состояния устарела, — и мерит их живой прогон SQL, а не
 * подменённый счётчик: тест на моках проверял бы собственную заглушку.
 * Здесь предмет другой — <b>сложение</b> попарных исходов в ответ
 * читателю.
 */
class JournalContinuityTest {

    private static final String GROUP = "audit-statistics.journal";
    private static final OffsetDateTime MOMENT =
            OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime STALE_BEFORE = MOMENT.minusMinutes(5);

    private final AuditRecordDataService auditRecordDataService = mock(AuditRecordDataService.class);
    private final ReceptionStateDataService receptionStateDataService = mock(ReceptionStateDataService.class);
    private final OwnerJournalCompletenessSource source =
            new OwnerJournalCompletenessSource(auditRecordDataService, receptionStateDataService);
    private final JournalCompletenessService service = new JournalCompletenessService();

    @Test
    @DisplayName("Наблюдаемых тем нет ни одной — непрерывность НЕ утверждаема")
    void anEmptyQuantifierDomainClaimsNothing() {
        when(receptionStateDataService.countSubscribedPairs(GROUP)).thenReturn(0L);

        assertThat(service.continuityClaimable(source, GROUP, STALE_BEFORE))
                .as("«все» по пустой коллекции истинно — и без этого конъюнкта поверхность "
                        + "отдавала бы «дыры нет» там, где не наблюдается ничего")
                .isFalse();
        verify(receptionStateDataService, never()).countSubscribedPairsWithBreak(anyString(), any());
    }

    @Test
    @DisplayName("Пары есть, дыр нет ни на одной — непрерывность утверждаема")
    void withoutBreaksContinuityIsClaimable() {
        given(2L, 0L);

        assertThat(service.continuityClaimable(source, GROUP, STALE_BEFORE)).isTrue();
    }

    /**
     * Дыра хоть на одной теме есть дыра в журнале, потому что журнал
     * один: скалярная форма давала бы истину в состоянии «по теме ядра
     * приём стои́т, по теме определений идёт».
     */
    @Test
    @DisplayName("Дыра хоть на одной паре — непрерывность НЕ утверждаема")
    void oneBrokenPairBreaksTheWhole() {
        given(2L, 1L);

        assertThat(service.continuityClaimable(source, GROUP, STALE_BEFORE)).isFalse();
    }

    /**
     * Обе величины едут одной выдачей и обязаны говорить об одном
     * состоянии одно и то же: границы нет — непрерывность не утверждаема.
     * Разойдясь, они предъявили бы читателю обещание полноты рядом с
     * признаком, что обещать нечего.
     */
    @Test
    @DisplayName("На состоянии «тем не наблюдается» обе величины говорят одно и то же")
    void bothValuesAgreeOnTheEmptyState() {
        when(receptionStateDataService.countSubscribedPairs(GROUP)).thenReturn(0L);
        when(receptionStateDataService.latestObservedSince(GROUP)).thenReturn(MOMENT);
        when(auditRecordDataService.earliestRecordedAt()).thenReturn(MOMENT);

        JournalCompleteness completeness = service.completeness(source, GROUP, STALE_BEFORE);

        assertThat(completeness.getLowerBound()).isNull();
        assertThat(completeness.getContinuityClaimable()).isFalse();
    }

    @Test
    @DisplayName("Момент устаревания уезжает в запрос как получен, а не подменяется")
    void theStaleMomentReachesTheQuery() {
        given(1L, 0L);

        service.continuityClaimable(source, GROUP, STALE_BEFORE);

        verify(receptionStateDataService).countSubscribedPairsWithBreak(GROUP, STALE_BEFORE);
    }

    private void given(Long subscribed, Long broken) {
        when(receptionStateDataService.countSubscribedPairs(GROUP)).thenReturn(subscribed);
        when(receptionStateDataService.countSubscribedPairsWithBreak(GROUP, STALE_BEFORE)).thenReturn(broken);
    }
}
