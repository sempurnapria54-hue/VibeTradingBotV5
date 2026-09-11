package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.domain.service.JournalCleanupService;
import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import com.example.auditstatistics.persistence.service.ReceptionStateDataService;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Границы писателя-чистки: что проход пишет, чего не пишет и какими
 * транзакциями (docs/models/domain/other/AuditRecord.md, таблица
 * писателей; docs/components/JournalCleanupJob.md §Границы).
 *
 * <p><b>Почему это отдельная проба.</b> Писателей у строки состояния приёма
 * трое, и делят они её колонки: величины приёма пишет слушатель, состав и
 * живость — тик, момент разрыва СНИМАЕТ чистка. Проход, тронувший момент
 * обновления, утверждал бы живость приёма в момент, когда приёма могло не
 * быть вовсе, — и предикат свежести строки стал бы истинным без
 * измерителя.
 *
 * <p><b>Транзакционные границы проверяются здесь же:</b> порция удаления —
 * своя транзакция (обрыв между порциями ничего не стои́т, обрыв внутри
 * одной большой откатывал бы уже сделанное), снятие момента разрыва — своя,
 * и граница полноты читается ВНУТРИ неё.
 */
class JournalCleanupBoundariesTest {

    private static final String GROUP = "audit-statistics.journal";
    private static final OffsetDateTime BOUND =
            OffsetDateTime.of(2026, 9, 8, 10, 0, 0, 0, ZoneOffset.UTC);

    private final AuditRecordDataService auditRecordDataService = mock(AuditRecordDataService.class);
    private final ReceptionStateDataService receptionStateDataService = mock(ReceptionStateDataService.class);
    private final JournalCompletenessService completenessService = mock(JournalCompletenessService.class);
    private final OwnerJournalCompletenessSource completenessSource =
            mock(OwnerJournalCompletenessSource.class);
    private final JournalCleanupService service = new JournalCleanupService(auditRecordDataService,
            receptionStateDataService,
            completenessService,
            completenessSource);

    @Test
    @DisplayName("Снятие момента разрыва идёт по нижней границе полноты, а не по глубине")
    void theGapsAreClearedByTheLowerBound() {
        when(completenessService.lowerBound(completenessSource, GROUP)).thenReturn(Optional.of(BOUND));

        service.clearGapsOutsideLowerBound(GROUP);

        verify(receptionStateDataService).clearGapsBefore(GROUP, BOUND);
    }

    @Test
    @DisplayName("Границы нет — не гаснет ничего: пустая граница не «ноль», а отсутствие вопроса")
    void withoutABoundNothingIsCleared() {
        when(completenessService.lowerBound(completenessSource, GROUP)).thenReturn(Optional.empty());

        service.clearGapsOutsideLowerBound(GROUP);

        verify(receptionStateDataService, never()).clearGapsBefore(anyString(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Чистка не трогает ни одной величины, кроме момента разрыва: у них другие писатели")
    void theCleanupWritesNothingButTheGap() {
        when(completenessService.lowerBound(completenessSource, GROUP)).thenReturn(Optional.of(BOUND));

        service.clearGapsOutsideLowerBound(GROUP);
        service.deleteBatchRecordedBefore(BOUND, 100);

        verify(receptionStateDataService, never()).openPair(anyString(), anyString(), any(OffsetDateTime.class));
        verify(receptionStateDataService, never()).markSubscribed(anyString(), any(), any(OffsetDateTime.class));
        verify(receptionStateDataService, never()).markUnsubscribed(anyString(), any(), any(OffsetDateTime.class));
        verify(receptionStateDataService, never()).markAccepted(anyString(), anyString(), any(OffsetDateTime.class));
        verify(receptionStateDataService, never()).markHalted(anyString(), anyString());
        verify(receptionStateDataService, never()).markGap(anyString(), anyString(), any(OffsetDateTime.class));
        verify(receptionStateDataService, never())
                .restartObservation(anyString(), anyString(), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Удаление идёт порциями: размер порции доезжает до запроса, а не теряется в границе")
    void theBatchSizeReachesTheQuery() {
        service.deleteBatchRecordedBefore(BOUND, 100);

        verify(auditRecordDataService).deleteBatchRecordedBefore(BOUND, 100);
    }

    @Test
    @DisplayName("У обоих ходов прохода своя транзакция с НАЗВАННЫМ менеджером")
    void bothMovesRunInNamedTransactions() {
        assertThat(transactionalMethods())
                .as("подключений три, и неквалифицированная граница сменила бы поведение на появлении второго")
                .hasSize(2)
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class).transactionManager())
                        .isEqualTo(JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER));
    }

    @Test
    @DisplayName("Граница не приходит параметром: её читает сам ход, то есть внутри своей транзакции")
    void theBoundIsReadInsideTheClearingTransaction() throws NoSuchMethodException {
        Method clearing = JournalCleanupService.class
                .getDeclaredMethod("clearGapsOutsideLowerBound", String.class);
        when(completenessService.lowerBound(completenessSource, GROUP)).thenReturn(Optional.of(BOUND));

        service.clearGapsOutsideLowerBound(GROUP);

        assertThat(clearing.isAnnotationPresent(Transactional.class))
                .as("граница, посчитанная снаружи, описывала бы состояние базы, которого на момент правки "
                        + "уже нет")
                .isTrue();
        verify(completenessService).lowerBound(completenessSource, GROUP);
    }

    private List<Method> transactionalMethods() {
        return Arrays.stream(JournalCleanupService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .toList();
    }
}
