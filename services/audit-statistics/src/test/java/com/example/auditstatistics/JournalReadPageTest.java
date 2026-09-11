package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.JournalReadProperties;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.domain.model.JournalPage;
import com.example.auditstatistics.domain.model.JournalQuery;
import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.domain.service.JournalReadService;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Курсорная страница журнальной выборки: сколько строк читается, что
 * отдаётся и чем отвечается «окно дочитано»
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Продолжение узнаётся ЛИШНЕЙ прочитанной строкой.</b> Полная
 * страница сама по себе о продолжении не говорит: окно, чей остаток равен
 * размеру страницы ровно, обещало бы читателю ещё один — пустой —
 * запрос, и он не отличил бы «дочитано» от «дальше пусто».
 */
class JournalReadPageTest {

    private static final String TENANT = "tenant-1";
    private static final String GROUP = "audit-statistics.journal";
    private static final Integer PAGE_SIZE = 3;
    private static final Duration STATE_MAX_AGE = Duration.ofMinutes(5);
    private static final OffsetDateTime FROM =
            OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final AuditRecordDataService auditRecordDataService = mock(AuditRecordDataService.class);
    private final JournalCompletenessService completenessService = mock(JournalCompletenessService.class);
    private final JournalReadProperties journalReadProperties = new JournalReadProperties();
    private final ReceptionProperties receptionProperties = new ReceptionProperties();

    private JournalReadService service;

    @BeforeEach
    void setUp() {
        journalReadProperties.setMaxWindow(Duration.ofDays(7));
        journalReadProperties.setPageSize(PAGE_SIZE);
        receptionProperties.setGroupId(GROUP);
        receptionProperties.setStateMaxAge(STATE_MAX_AGE);
        service = new JournalReadService(journalReadProperties, receptionProperties,
                auditRecordDataService, completenessService,
                mock(OwnerJournalCompletenessSource.class));
        when(completenessService.completeness(any(), anyString(), any()))
                .thenReturn(new JournalCompleteness(FROM, Boolean.TRUE));
    }

    @Test
    @DisplayName("Читается на одну строку больше страницы — ею и узнаётся продолжение")
    void oneRowBeyondThePageIsRead() {
        given(0);

        service.read(acceptableQuery());

        verify(auditRecordDataService).findPage(any(), eq(PAGE_SIZE + 1));
    }

    @Test
    @DisplayName("Строк больше страницы: отдаётся страница, а лишняя остаётся внутри")
    void theExtraRowIsNotHandedOut() {
        given(PAGE_SIZE + 1);

        JournalPage page = service.read(acceptableQuery());

        assertThat(page.getRecords()).hasSize(PAGE_SIZE);
        assertThat(page.getRecords())
                .as("наружу уходит страница, а не всё прочитанное")
                .extracting(AuditRecord::getEventId)
                .containsExactly("event-0", "event-1", "event-2");
    }

    @Test
    @DisplayName("Позиция продолжения — пара последней ОТДАННОЙ строки, а не последней прочитанной")
    void theCursorPointsAtTheLastHandedRow() {
        given(PAGE_SIZE + 1);

        assertThat(service.read(acceptableQuery()).getNextCursor())
                .as("курсор от лишней строки пропустил бы последнюю строку страницы")
                .satisfies(cursor -> {
                    assertThat(cursor.getEventId()).isEqualTo("event-2");
                    assertThat(cursor.getOccurredAt()).isEqualTo(FROM.plusMinutes(2));
                });
    }

    @Test
    @DisplayName("Строк ровно на страницу — окно дочитано, позиции продолжения нет")
    void aFullPageWithoutAnExtraRowEndsTheWindow() {
        given(PAGE_SIZE);

        JournalPage page = service.read(acceptableQuery());

        assertThat(page.getRecords()).hasSize(PAGE_SIZE);
        assertThat(page.getNextCursor())
                .as("полная страница сама по себе о продолжении не говорит")
                .isNull();
    }

    @Test
    @DisplayName("Строк нет вовсе — пустая страница без позиции продолжения")
    void anEmptyWindowHasNoCursor() {
        given(0);

        JournalPage page = service.read(acceptableQuery());

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    /**
     * Свежесть строки состояния меряется <b>допустимым возрастом</b>, а
     * не моментом выдачи: без вычитания предикат непрерывности объявлял
     * бы устаревшей всякую строку, обновлённую хоть на миг раньше
     * запроса, — то есть был бы ложен всегда.
     */
    @Test
    @DisplayName("Момент устаревания — момент выдачи за вычетом допустимого возраста")
    void theStaleMomentSubtractsTheAllowedAge() {
        given(0);
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        service.read(acceptableQuery());

        ArgumentCaptor<OffsetDateTime> staleBefore = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(completenessService).completeness(any(), eq(GROUP), staleBefore.capture());
        assertThat(staleBefore.getValue())
                .isBeforeOrEqualTo(before.minus(STATE_MAX_AGE).plusSeconds(1))
                .isAfterOrEqualTo(before.minus(STATE_MAX_AGE).minusSeconds(30));
    }

    /**
     * Имя группы — операнд durable-строки состояния приёма, и берётся оно
     * из конфигурации, а не из литерала выборки: иначе у величины
     * появился бы второй носитель.
     */
    @Test
    @DisplayName("Полнота спрашивается по имени группы из конфигурации")
    void theGroupNameComesFromTheConfiguration() {
        given(0);
        receptionProperties.setGroupId("another-group");

        service.read(acceptableQuery());

        verify(completenessService).completeness(any(), eq("another-group"), any());
    }

    @Test
    @DisplayName("Размер страницы приходит из конфигурации")
    void thePageSizeComesFromTheConfiguration() {
        journalReadProperties.setPageSize(1);
        given(0);

        service.read(acceptableQuery());

        verify(auditRecordDataService).findPage(any(), eq(2));
    }

    private void given(Integer rows) {
        List<AuditRecord> found = new ArrayList<>(IntStream.range(0, rows)
                .mapToObj(index -> AuditRecord.builder()
                        .eventId("event-" + index)
                        .occurredAt(FROM.plusMinutes(index))
                        .build())
                .toList());
        when(auditRecordDataService.findPage(any(), anyInt())).thenReturn(found);
    }

    private JournalQuery acceptableQuery() {
        return JournalQuery.builder()
                .tenantId(TENANT)
                .from(FROM)
                .to(FROM.plusDays(1))
                .build();
    }
}
