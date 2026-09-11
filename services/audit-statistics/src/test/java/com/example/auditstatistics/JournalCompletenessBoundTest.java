package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import com.example.auditstatistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Нижняя граница полноты журнала — свёртка двух операндов
 * (docs/spec/audit-journal.json, {@code journalLowerBound}).
 *
 * <p><b>Оба операнда несущие, и закрывают они разные ветви.</b> Без
 * первого клейм «журнал полон по событиям, произведённым после X» ложен в
 * непроизводственном окружении после чистки; без второго — на теме,
 * присоединённой к подписке позже начала производства, и ни одна ветвь
 * разрыва этого состояния не ловит. Поэтому обе стороны максимума
 * проверяются раздельно, а не одной пробой.
 *
 * <p><b>Ветви пустоты у операндов РАЗНЫЕ, и это тоже проверяется.</b>
 * Пустой журнал границы не отменяет — наблюдение уже идёт; отсутствие
 * <b>наблюдаемых тем</b> отменяет: группа, не наблюдающая ни одной темы,
 * не обещает ничего.
 *
 * <p><b>Области двух операндов не совпадают, и это проверяется
 * отдельно.</b> Максимум считается по ВСЕМ строкам состояния, включая
 * снятые с подписки, — их строки журнала из журнала не исчезли; а вопрос
 * «наблюдается ли хоть одна тема» задаётся только подписанным. Проба на
 * состоянии «строки есть, но все сняты с подписки» разводит эти области:
 * без неё чтение «строки есть — значит граница есть» выглядело бы верным.
 */
class JournalCompletenessBoundTest {

    private static final String GROUP = "audit-statistics.journal";
    private static final OffsetDateTime EARLY =
            OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATE =
            OffsetDateTime.of(2026, 9, 8, 10, 0, 0, 0, ZoneOffset.UTC);

    private final AuditRecordDataService auditRecordDataService = mock(AuditRecordDataService.class);
    private final ReceptionStateDataService receptionStateDataService = mock(ReceptionStateDataService.class);
    private final OwnerJournalCompletenessSource source =
            new OwnerJournalCompletenessSource(auditRecordDataService, receptionStateDataService);
    private final JournalCompletenessService service = new JournalCompletenessService();

    @Test
    @DisplayName("Момент наблюдения позже самого раннего приёма — границей становится он")
    void theLaterObservationWins() {
        given(EARLY, LATE, 2L);

        assertThat(service.lowerBound(source, GROUP))
                .as("тема, присоединённая позже начала производства, двигает границу вперёд")
                .contains(LATE);
    }

    @Test
    @DisplayName("Самый ранний приём позже момента наблюдения — границей становится он")
    void theLaterRecordWins() {
        given(LATE, EARLY, 2L);

        assertThat(service.lowerBound(source, GROUP))
                .as("после чистки началом ряда становится самая ранняя уцелевшая строка")
                .contains(LATE);
    }

    @Test
    @DisplayName("Журнал пуст — границей служит позднейший момент наблюдения")
    void anEmptyJournalKeepsTheObservationBound() {
        given(null, LATE, 1L);

        assertThat(service.lowerBound(source, GROUP)).contains(LATE);
    }

    @Test
    @DisplayName("Строк состояния нет ни одной — границы НЕТ, и это значение, а не ноль")
    void withoutPairsThereIsNoBound() {
        given(EARLY, null, 0L);

        assertThat(service.lowerBound(source, GROUP))
                .as("группа, не наблюдающая ни одной темы, не обещает ничего — и минимум по журналу "
                        + "обещал бы полноту, которой никто не мерил")
                .isEmpty();
    }

    /**
     * Состояние достижимо: темы ушли из подписки — конфигурация сменилась,
     * производитель снят, — а строки остались, потому что их удаление
     * опустило бы границу. Наблюдения при этом не идёт ни по одной теме.
     */
    @Test
    @DisplayName("Строки есть, но все сняты с подписки — границы НЕТ")
    void unsubscribedPairsPromiseNothing() {
        given(EARLY, LATE, 0L);

        assertThat(service.lowerBound(source, GROUP))
                .as("наблюдаемых тем нет ни одной — обещать нечего, ровно как при отсутствии строк; "
                        + "иначе граница и предикат непрерывности сказали бы об одном состоянии разное")
                .isEmpty();
    }

    /**
     * Обратная сторона той же развилки: пока подписанная пара есть,
     * снятая с подписки строка из МАКСИМУМА не исключается — её строки
     * журнала из журнала не исчезли.
     */
    @Test
    @DisplayName("Есть хоть одна подписанная пара — граница считается по всем строкам")
    void oneSubscribedPairIsEnoughForTheBound() {
        given(EARLY, LATE, 1L);

        assertThat(service.lowerBound(source, GROUP))
                .as("максимум берётся по всем строкам, включая снятые: иначе граница поехала бы назад")
                .contains(LATE);
    }

    private void given(OffsetDateTime earliestRecorded, OffsetDateTime latestObserved, Long subscribedPairs) {
        when(auditRecordDataService.earliestRecordedAt()).thenReturn(earliestRecorded);
        when(receptionStateDataService.latestObservedSince(GROUP)).thenReturn(latestObserved);
        when(receptionStateDataService.countSubscribedPairs(GROUP)).thenReturn(subscribedPairs);
    }
}
