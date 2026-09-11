package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.config.JournalReadProperties;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.domain.model.JournalQuery;
import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.domain.service.ReadQueryRejectedException;
import com.example.auditstatistics.domain.service.JournalReadService;
import com.example.auditstatistics.persistence.service.AuditRecordDataService;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Что журнальная выборка отвергает, а не сужает
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Предмет теста — именно ОТКАЗ, а не пустая выдача.</b> Молчаливо
 * суженное окно отдало бы неполный ряд под видом запрошенного, и читатель
 * принял бы тишину за отсутствие событий (docs/concept.md, П1 — умолчание
 * не бывает благоприятным). Поэтому каждая проба смотрит не только на
 * исключение, но и на то, что до базы вопрос не дошёл вовсе.
 *
 * <p><b>Границы проверяются с обеих сторон.</b> Окно, равное пределу,
 * обязано проходить: предел есть наибольшее допустимое, а не первое
 * запрещённое, — и без этой пробы нестрогое сравнение выглядело бы верным.
 */
class JournalReadBoundariesTest {

    private static final String TENANT = "tenant-1";
    private static final String GROUP = "audit-statistics.journal";
    private static final Duration MAX_WINDOW = Duration.ofDays(7);
    private static final OffsetDateTime FROM =
            OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final AuditRecordDataService auditRecordDataService = mock(AuditRecordDataService.class);
    private final JournalCompletenessService completenessService = mock(JournalCompletenessService.class);
    private final JournalReadProperties journalReadProperties = new JournalReadProperties();
    private final ReceptionProperties receptionProperties = new ReceptionProperties();

    private JournalReadService service;

    @BeforeEach
    void setUp() {
        journalReadProperties.setMaxWindow(MAX_WINDOW);
        journalReadProperties.setPageSize(10);
        receptionProperties.setGroupId(GROUP);
        receptionProperties.setStateMaxAge(Duration.ofMinutes(5));
        service = new JournalReadService(journalReadProperties, receptionProperties,
                auditRecordDataService, completenessService,
                mock(OwnerJournalCompletenessSource.class));
        when(auditRecordDataService.findPage(any(), anyInt())).thenReturn(List.of());
        when(completenessService.completeness(any(), anyString(), any()))
                .thenReturn(new JournalCompleteness(FROM, Boolean.TRUE));
    }

    @Test
    @DisplayName("Окна нет вовсе — вопрос отвергается, а не читается без предела")
    void aQueryWithoutAWindowIsRejected() {
        assertThatThrownBy(() -> service.read(query().build()))
                .isInstanceOf(ReadQueryRejectedException.class);

        verify(auditRecordDataService, never()).findPage(any(), anyInt());
    }

    @Test
    @DisplayName("Названа одна граница окна — окном это не является")
    void aHalfOpenWindowIsRejected() {
        assertThatThrownBy(() -> service.read(query().from(FROM).build()))
                .isInstanceOf(ReadQueryRejectedException.class);

        verify(auditRecordDataService, never()).findPage(any(), anyInt());
    }

    @Test
    @DisplayName("Окно перевёрнуто — отказ, а не пустая выдача")
    void anInvertedWindowIsRejected() {
        assertThatThrownBy(() -> service.read(
                query().from(FROM).to(FROM.minusSeconds(1)).build()))
                .as("пустая выдача читалась бы как «за период ничего не происходило»")
                .isInstanceOf(ReadQueryRejectedException.class);

        verify(auditRecordDataService, never()).findPage(any(), anyInt());
    }

    @Test
    @DisplayName("Окно шире предела — отказ, а не молчаливое сужение")
    void aWindowWiderThanTheLimitIsRejected() {
        assertThatThrownBy(() -> service.read(
                query().from(FROM).to(FROM.plus(MAX_WINDOW).plusSeconds(1)).build()))
                .isInstanceOf(ReadQueryRejectedException.class);

        verify(auditRecordDataService, never()).findPage(any(), anyInt());
    }

    @Test
    @DisplayName("Окно РОВНО в предел проходит: предел — наибольшее допустимое")
    void aWindowExactlyAtTheLimitIsAccepted() {
        assertThatCode(() -> service.read(query().from(FROM).to(FROM.plus(MAX_WINDOW)).build()))
                .doesNotThrowAnyException();
    }

    /**
     * Предел берётся из конфигурации, а не из константы кода: иначе
     * перекалибровка требовала бы сборки, а сама величина перестала бы
     * быть величиной владельца.
     */
    @Test
    @DisplayName("Предел ширины окна приходит из конфигурации")
    void theWindowLimitComesFromTheConfiguration() {
        journalReadProperties.setMaxWindow(Duration.ofDays(1));

        assertThatThrownBy(() -> service.read(query().from(FROM).to(FROM.plusDays(2)).build()))
                .as("окно, дозволенное прежним пределом, при новом обязано отвергаться")
                .isInstanceOf(ReadQueryRejectedException.class);
    }

    @Test
    @DisplayName("Курсор назван наполовину — отказ: позиция задаётся парой")
    void aPartialCursorIsRejected() {
        assertThatThrownBy(() -> service.read(query()
                .from(FROM).to(FROM.plusDays(1))
                .cursorOccurredAt(FROM.plusHours(1))
                .build()))
                .isInstanceOf(ReadQueryRejectedException.class);

        assertThatThrownBy(() -> service.read(query()
                .from(FROM).to(FROM.plusDays(1))
                .cursorEventId("event-1")
                .build()))
                .as("вторая половина без первой — тот же дефект вопроса")
                .isInstanceOf(ReadQueryRejectedException.class);

        verify(auditRecordDataService, never()).findPage(any(), anyInt());
    }

    @Test
    @DisplayName("Курсора нет вовсе — читается первая страница окна, и это не отказ")
    void anAbsentCursorIsNotAPartialOne() {
        assertThatCode(() -> service.read(query().from(FROM).to(FROM.plusDays(1)).build()))
                .doesNotThrowAnyException();
    }

    /**
     * Текст отказа называет ПОВОД, а не класс: не сказав, чем вопрос не
     * принят, поверхность оставила бы читателя перебирать четыре повода.
     */
    @Test
    @DisplayName("Отказ называет повод, а не только факт отказа")
    void theRejectionNamesItsReason() {
        assertThatThrownBy(() -> service.read(
                query().from(FROM).to(FROM.plus(MAX_WINDOW).plusSeconds(1)).build()))
                .hasMessageContaining("шире");

        assertThatThrownBy(() -> service.read(query().build()))
                .hasMessageContaining("Окно");
    }

    /**
     * Полнота едет вместе со строками — обязательство распространено на
     * всякую выдачу чисел этого сервиса.
     */
    @Test
    @DisplayName("Принятый вопрос отдаёт полноту вместе со строками")
    void anAcceptedQueryCarriesCompleteness() {
        assertThat(service.read(query().from(FROM).to(FROM.plusDays(1)).build()).getCompleteness())
                .as("число без своей достоверности читается как «всё в порядке»")
                .isNotNull();
    }

    /**
     * Менеджер транзакций назван поимённо: подключений у процесса три, и
     * умолчания у выбора нет намеренно. Неквалифицированная граница взяла
     * бы то из них, чьё объявление обработано первым, — и чтение журнала
     * пошло бы по чужой тропе.
     */
    @Test
    @DisplayName("Чтение идёт транзакцией с НАЗВАННЫМ менеджером базы журнала, и она только читающая")
    void theReadRunsInANamedReadOnlyTransaction() {
        assertThat(Arrays.stream(JournalReadService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .toList())
                .hasSize(1)
                .allSatisfy(method -> {
                    Transactional boundary = method.getAnnotation(Transactional.class);
                    assertThat(boundary.transactionManager())
                            .isEqualTo(JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER);
                    assertThat(boundary.readOnly())
                            .as("поверхность сервиса объявлена только читающей")
                            .isTrue();
                });
    }

    private JournalQuery.JournalQueryBuilder query() {
        return JournalQuery.builder().tenantId(TENANT);
    }
}
