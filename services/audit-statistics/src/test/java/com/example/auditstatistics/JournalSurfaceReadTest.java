package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auditstatistics.api.GlobalExceptionHandler;
import com.example.auditstatistics.api.controller.JournalController;
import com.example.auditstatistics.domain.model.AuditRecord;
import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.domain.model.JournalCursor;
import com.example.auditstatistics.domain.model.JournalPage;
import com.example.auditstatistics.domain.service.ReadQueryRejectedException;
import com.example.auditstatistics.domain.service.JournalReadService;
import com.example.auditstatistics.mapping.AuditRecordMapper;
import com.example.auditstatistics.mapping.AuditRecordMapperImpl;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Форма ответа журнальной выборки на проводе
 * (docs/models/domain/other/AuditRecord.md §«Как журнал читается»).
 *
 * <p><b>Проверяется то, что чтением не проверяется.</b> Содержимое
 * события едет <b>объектом</b>, а не строкой с экранированием, и держится
 * это на аннотации сериализатора: соседняя по виду аннотация из другого
 * пакета была бы на этом проводе <b>молча проигнорирована</b>
 * (.claude/rules/tech-radar.md, записи Jackson). Молча — значит дефект
 * выглядел бы закрытым; поэтому исход закреплён прогоном.
 *
 * <p><b>Полнота едет вместе со строками</b> — обязательство, которое
 * иначе держалось бы дисциплиной собирающего ответ.
 *
 * <p>Контекст поднимается <b>без БД, брокера и контура доступа</b>:
 * предмет теста — форма ответа и отказа, а не они.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = JournalSurfaceReadTest.ReadSurfaceConfig.class)
class JournalSurfaceReadTest {

    private static final String RECORDS = "/api/v1/audit-statistics/journal/records";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final OffsetDateTime MOMENT =
            OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JournalReadService journalReadService;

    private MockMvc mockMvc;

    /**
     * Подменённая выборка живёт в кэшированном контексте, то есть одна на
     * все пробы: без сброса заданное одной пробой поведение доставалось бы
     * следующей — и та мерила бы соседку, а не свой предмет.
     */
    @BeforeEach
    void setUp() {
        reset(journalReadService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /**
     * <b>Содержимое — объект, а не строка.</b> Переупакованное в строку,
     * оно заставило бы читателя разбирать JSON внутри JSON, а журнал —
     * знать о форме содержимого больше, чем он знает.
     */
    @Test
    @DisplayName("Содержимое события едет объектом, а не экранированной строкой")
    void theContentTravelsAsAnObject() throws Exception {
        given(page(List.of(record("event-1", "{\"dealInternalId\":\"deal-7\",\"pnl\":12.5}")), null));

        mockMvc.perform(get(RECORDS)
                        .header(TENANT_HEADER, "tenant-1")
                        .param("from", MOMENT.minusDays(1).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].content.dealInternalId").value("deal-7"))
                .andExpect(jsonPath("$.records[0].content.pnl").value(12.5));
    }

    @Test
    @DisplayName("Полнота едет тем же ответом, что и строки")
    void completenessTravelsWithTheRows() throws Exception {
        given(page(List.of(record("event-1", "{}")), null));

        mockMvc.perform(get(RECORDS)
                        .header(TENANT_HEADER, "tenant-1")
                        .param("from", MOMENT.minusDays(1).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness.lowerBound").exists())
                .andExpect(jsonPath("$.completeness.continuityClaimable").value(true));
    }

    /**
     * Отсутствие границы есть значение, а не ноль: пустота уезжает
     * пустотой, и подставлять вместо неё момент эпохи поверхность не
     * станет (docs/rules/absent-value-semantics.md).
     */
    @Test
    @DisplayName("Границы нет — она уезжает пустотой, а не нулём")
    void theAbsentBoundTravelsAsAbsence() throws Exception {
        given(JournalPage.builder()
                .records(List.of())
                .completeness(new JournalCompleteness(null, Boolean.FALSE))
                .build());

        mockMvc.perform(get(RECORDS)
                        .header(TENANT_HEADER, "tenant-1")
                        .param("from", MOMENT.minusDays(1).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness.lowerBound").doesNotExist())
                .andExpect(jsonPath("$.completeness.continuityClaimable").value(false));
    }

    @Test
    @DisplayName("Позиция продолжения отдаётся обеими половинами пары")
    void theCursorTravelsAsAPair() throws Exception {
        given(page(List.of(record("event-1", "{}")), new JournalCursor(MOMENT, "event-1")));

        mockMvc.perform(get(RECORDS)
                        .header(TENANT_HEADER, "tenant-1")
                        .param("from", MOMENT.minusDays(1).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor.eventId").value("event-1"))
                .andExpect(jsonPath("$.nextCursor.occurredAt").exists());
    }

    /**
     * Тенант приезжает операндом вызова и в строке ответа не повторяется:
     * всякая строка ответа несёт его по построению отбора.
     */
    @Test
    @DisplayName("Строка ответа не повторяет тенанта и не отдаёт ключа базы")
    void theRowCarriesNeitherTenantNorDatabaseKey() throws Exception {
        given(page(List.of(record("event-1", "{}")), null));

        mockMvc.perform(get(RECORDS)
                        .header(TENANT_HEADER, "tenant-1")
                        .param("from", MOMENT.minusDays(1).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.records[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.records[0].id").doesNotExist());
    }

    /**
     * Отвергнутый вопрос отвечает ТЕМ ЖЕ error-DTO, что и всякая ошибка
     * поверхности, — и называет повод: перебирать четыре повода читатель
     * не должен.
     */
    @Test
    @DisplayName("Отвергнутый вопрос отвечает единым error-DTO и называет повод")
    void aRejectedQueryAnswersWithTheSameErrorDto() throws Exception {
        when(journalReadService.read(any()))
                .thenThrow(new ReadQueryRejectedException("Окно шире допустимого: PT168H"));

        mockMvc.perform(get(RECORDS)
                        .header(TENANT_HEADER, "tenant-1")
                        .param("from", MOMENT.minusDays(30).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_NOT_ACCEPTED"))
                .andExpect(jsonPath("$.message").value("Окно шире допустимого: PT168H"))
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    @Test
    @DisplayName("Заголовка контекста нет — вопрос не принят: радиус ничем не заменяется")
    void theTenantHeaderIsMandatory() throws Exception {
        mockMvc.perform(get(RECORDS)
                        .param("from", MOMENT.minusDays(1).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isBadRequest());
    }

    /**
     * Пустой заголовок радиусом не является: без этой пробы требование
     * держалось бы аннотацией, обработчика которой у контейнера могло бы
     * не быть вовсе — и оно молча не исполнялось бы.
     */
    @Test
    @DisplayName("Заголовок контекста пуст — вопрос не принят")
    void aBlankTenantHeaderIsNotAccepted() throws Exception {
        mockMvc.perform(get(RECORDS)
                        .header(TENANT_HEADER, "  ")
                        .param("from", MOMENT.minusDays(1).toString())
                        .param("to", MOMENT.toString()))
                .andExpect(status().isBadRequest());
    }

    /**
     * <b>Отказ по правам общий перехватчик не съедает.</b> Ответ на него
     * пишет контур, а он стои́т СНАРУЖИ {@code DispatcherServlet}: отказ,
     * разрешённый здесь, ушёл бы вызывающему кодом {@code 500} вместо
     * {@code 403}, строки отказа не завелось бы, и в журнале следа не
     * осталось бы.
     *
     * <p><b>Проба нужна ИМЕННО потому, что тропа сегодня недостижима:</b>
     * пер-операционных проверок права нет ни одной, и регресс этой формы
     * не обнаружился бы прогоном — он ждал бы первой такой проверки, то
     * есть чужого шага (docs/rules/api-access-policy.md).
     *
     * <p><b>Источник отказа в пробе — подменённая выборка, а не проверка
     * права,</b> и предмет от этого не меняется: мерится, что исключение
     * ВЫХОДИТ из диспетчера, а кто его бросил внутри — безразлично.
     */
    @Test
    @DisplayName("Отказ по правам уходит наружу диспетчера, а не разрешается перехватчиком")
    void anAccessDenialLeavesTheDispatcherUnresolved() {
        when(journalReadService.read(any())).thenThrow(new AccessDeniedException("нет права"));

        assertThatThrownBy(() -> mockMvc.perform(get(RECORDS)
                .header(TENANT_HEADER, "tenant-1")
                .param("from", MOMENT.minusDays(1).toString())
                .param("to", MOMENT.toString())))
                .as("разрешённый здесь отказ не дошёл бы до контура, и ответ его не написал бы никто")
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    private void given(JournalPage page) {
        when(journalReadService.read(any())).thenReturn(page);
    }

    private JournalPage page(List<AuditRecord> records, JournalCursor nextCursor) {
        return JournalPage.builder()
                .records(records)
                .nextCursor(nextCursor)
                .completeness(new JournalCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build();
    }

    private AuditRecord record(String eventId, String content) {
        return AuditRecord.builder()
                .eventId(eventId)
                .tenantId("tenant-1")
                .eventType("DealClosed")
                .occurredAt(MOMENT)
                .recordedAt(MOMENT.plusSeconds(1))
                .version(1)
                .content(content)
                .build();
    }

    /**
     * Минимальный контекст: контроллер, маппер и единый обработчик
     * ошибок. Выборка подменена — её поведение мерят свои тесты, а здесь
     * предмет — форма ответа.
     */
    @Configuration
    @EnableWebMvc
    static class ReadSurfaceConfig {

        @Bean
        JournalReadService journalReadService() {
            return mock(JournalReadService.class);
        }

        @Bean
        AuditRecordMapper auditRecordMapper() {
            return new AuditRecordMapperImpl();
        }

        @Bean
        JournalController journalController(JournalReadService journalReadService,
                                            AuditRecordMapper auditRecordMapper) {
            return new JournalController(journalReadService, auditRecordMapper);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
