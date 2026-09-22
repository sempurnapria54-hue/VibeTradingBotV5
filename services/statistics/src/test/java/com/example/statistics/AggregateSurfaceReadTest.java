package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statistics.api.controller.AggregateController;
import com.example.statistics.domain.model.AggregateCursor;
import com.example.statistics.domain.model.AggregateGrain;
import com.example.statistics.domain.model.AggregatePage;
import com.example.statistics.domain.model.DealAggregate;
import com.example.statistics.domain.model.IncidentAggregate;
import com.example.statistics.domain.model.ReceptionCompleteness;
import com.example.statistics.domain.service.AggregateReadService;
import com.example.statistics.exception.ReadQueryRejectedException;
import com.example.statistics.exception.handler.GlobalExceptionHandler;
import com.example.statistics.mapping.AggregateMapper;
import com.example.statistics.mapping.AggregateMapperImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Форма ответа агрегатной выборки на проводе
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Проверяется то, что чтением не проверяется.</b> Перечень чужого
 * зерна обязан уехать ОТСУТСТВИЕМ, а не пустым перечнем: различие несёт
 * сериализатор, и «пусто» вместо «нет» читатель принял бы за «строк за
 * период не было». То же у пустых компонентов позиции и у пустой границы
 * полноты (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Полнота едет вместе со строками</b> — обязательство, которое иначе
 * держалось бы дисциплиной собирающего ответ.
 *
 * <p>Контекст поднимается <b>без БД, брокера и контура доступа</b>: предмет
 * теста — форма ответа и отказа, а не они.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AggregateSurfaceReadTest.AggregateSurfaceConfig.class)
class AggregateSurfaceReadTest {

    private static final String ROWS = "/api/v1/statistics/aggregates/rows";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final LocalDate BUCKET = LocalDate.of(2026, 9, 9);
    private static final OffsetDateTime MOMENT =
            OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AggregateReadService aggregateReadService;

    private MockMvc mockMvc;

    /**
     * Подменённая выборка живёт в кэшированном контексте, то есть одна на
     * все пробы: без сброса заданное одной пробой поведение доставалось бы
     * следующей — и та мерила бы соседку, а не свой предмет.
     */
    @BeforeEach
    void setUp() {
        reset(aggregateReadService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /**
     * <b>Две величины проверяются поимённо, и это не придирка к именам.</b>
     * У полей {@code rSum} и {@code rDenominatorDeals} второй знак
     * заглавный, а капитализация аксессоров в репозитории — {@code beanspec}:
     * геттер выходит {@code getrSum}, и сериализатор такого геттера не
     * опознаёт вовсе. Величина тогда не уезжает МОЛЧА — ни отказа, ни поля в
     * схеме, — и ловится это только пробой на проводе.
     */
    @Test
    @DisplayName("Строки сделочного зерна едут своим перечнем, а перечня чужого зерна в ответе нет")
    void theDealGrainTravelsInItsOwnList() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.DEAL)
                .dealRows(List.of(dealRow()))
                .completeness(new ReceptionCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grain").value("DEAL"))
                .andExpect(jsonPath("$.dealRows[0].exchangeAccountInternalId").value("acc-1"))
                .andExpect(jsonPath("$.dealRows[0].closedDeals").value(4))
                .andExpect(jsonPath("$.dealRows[0].rSum").value(1.5))
                .andExpect(jsonPath("$.dealRows[0].rDenominatorDeals").value(3))
                .andExpect(jsonPath("$.incidentRows").doesNotExist());
    }

    @Test
    @DisplayName("Строки зерна происшествий едут своим перечнем, а сделочного в ответе нет")
    void theIncidentGrainTravelsInItsOwnList() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.INCIDENT)
                .incidentRows(List.of(IncidentAggregate.builder()
                        .exchangeAccountInternalId("acc-1")
                        .bucketDate(BUCKET)
                        .openedDeals(2)
                        .raisedHolds(1)
                        .manuallyRaisedHolds(1)
                        .build()))
                .completeness(new ReceptionCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grain").value("INCIDENT"))
                .andExpect(jsonPath("$.incidentRows[0].openedDeals").value(2))
                .andExpect(jsonPath("$.incidentRows[0].manuallyRaisedHolds").value(1))
                .andExpect(jsonPath("$.dealRows").doesNotExist());
    }

    /**
     * Пустой перечень означает «зерно выбрано, строк в окне нет» — и уехать
     * он обязан именно перечнем: сведённый с отсутствием, он потерял бы
     * различие между «строк нет» и «спрошено другое зерно».
     */
    @Test
    @DisplayName("Строк нет — перечень едет ПУСТЫМ, а не отсутствует")
    void anEmptyPageTravelsAsAnEmptyList() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.DEAL)
                .dealRows(List.of())
                .completeness(new ReceptionCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealRows").isArray())
                .andExpect(jsonPath("$.dealRows").isEmpty())
                .andExpect(jsonPath("$.incidentRows").doesNotExist());
    }

    @Test
    @DisplayName("Полнота едет тем же ответом, что и строки агрегатов")
    void completenessTravelsWithTheRows() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.DEAL)
                .dealRows(List.of(dealRow()))
                .completeness(new ReceptionCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness.lowerBound").exists())
                .andExpect(jsonPath("$.completeness.continuityClaimable").value(true));
    }

    @Test
    @DisplayName("Границы нет — она уезжает пустотой, а не нулём")
    void theAbsentBoundTravelsAsAbsence() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.DEAL)
                .dealRows(List.of())
                .completeness(new ReceptionCompleteness(null, Boolean.FALSE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness.lowerBound").doesNotExist())
                .andExpect(jsonPath("$.completeness.continuityClaimable").value(false));
    }

    @Test
    @DisplayName("Позиция продолжения отдаётся всеми компонентами ключа зерна")
    void theCursorTravelsWithItsWholeKey() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.DEAL)
                .dealRows(List.of(dealRow()))
                .nextCursor(new AggregateCursor(BUCKET, "acc-1", "strategy-1", "USDT"))
                .completeness(new ReceptionCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor.bucketDate").value("2026-09-09"))
                .andExpect(jsonPath("$.nextCursor.exchangeAccountInternalId").value("acc-1"))
                .andExpect(jsonPath("$.nextCursor.strategyInternalId").value("strategy-1"))
                .andExpect(jsonPath("$.nextCursor.resultCurrency").value("USDT"));
    }

    /**
     * Пустые компоненты позиции уезжают пустотой: подставленная вместо них
     * строка означала бы другую строку зерна, и следующая страница
     * начиналась бы не там, где кончилась эта.
     */
    @Test
    @DisplayName("Пустые компоненты позиции уезжают пустотой, а не пустой строкой")
    void theAbsentCursorComponentsTravelAsAbsence() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.INCIDENT)
                .incidentRows(List.of())
                .nextCursor(new AggregateCursor(BUCKET, "acc-1", null, null))
                .completeness(new ReceptionCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor.strategyInternalId").doesNotExist())
                .andExpect(jsonPath("$.nextCursor.resultCurrency").doesNotExist());
    }

    /**
     * Строка ответа не повторяет тенанта и не отдаёт ключа базы: радиус
     * приезжает операндом вызова, а идентичность строки — её ключ зерна.
     */
    @Test
    @DisplayName("Строка ответа не повторяет тенанта и не отдаёт ключа базы")
    void theRowCarriesNeitherTenantNorDatabaseKey() throws Exception {
        given(AggregatePage.builder()
                .grain(AggregateGrain.DEAL)
                .dealRows(List.of(dealRow()))
                .completeness(new ReceptionCompleteness(MOMENT.minusDays(3), Boolean.TRUE))
                .build());

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealRows[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.dealRows[0].id").doesNotExist());
    }

    @Test
    @DisplayName("Отвергнутый вопрос отвечает единым error-DTO и называет повод")
    void aRejectedQueryAnswersWithTheSameErrorDto() throws Exception {
        when(aggregateReadService.read(any()))
                .thenThrow(new ReadQueryRejectedException("Окно шире допустимого: 92 суток"));

        mockMvc.perform(request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_NOT_ACCEPTED"))
                .andExpect(jsonPath("$.message").value("Окно шире допустимого: 92 суток"))
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    /**
     * Отказ по правам обязан <b>выйти</b> из диспетчера: ответ на него пишет
     * контур ({@code AccessDenialHandler}) из
     * {@code ExceptionTranslationFilter}, стоящего снаружи. Разрешённый
     * последним обработчиком, он ушёл бы вызывающему кодом {@code 500}
     * вместо {@code 403}, строки следа не завелось бы, и в журнале следа не
     * осталось бы.
     *
     * <p><b>Проба нужна ИМЕННО потому, что тропа сегодня недостижима:</b>
     * пер-операционных проверок права нет ни одной, и регресс этой формы не
     * обнаружился бы прогоном ящика — он ждал бы первой такой проверки, то
     * есть чужого шага (docs/rules/api-access-policy.md). Клетка {@code B11.8}
     * документа кейсов именно на эту пробу и опирается
     * (.claude/tests/cases/statistics.md §«Кейсы, не прогоняемые сегодня»).
     *
     * <p><b>Источник отказа в пробе — подменённая выборка, а не проверка
     * права,</b> и предмет от этого не меняется: мерится, что исключение
     * ВЫХОДИТ из диспетчера, а кто его бросил внутри — безразлично.
     */
    @Test
    @DisplayName("Отказ по правам уходит наружу диспетчера, а не разрешается перехватчиком")
    void anAccessDenialLeavesTheDispatcherUnresolved() {
        when(aggregateReadService.read(any())).thenThrow(new AccessDeniedException("нет права"));

        assertThatThrownBy(() -> mockMvc.perform(request()))
                .as("разрешённый здесь отказ не дошёл бы до контура, и ответ его не "
                        + "написал бы никто")
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Заголовка контекста нет — вопрос не принят: радиус ничем не заменяется")
    void theTenantHeaderIsMandatory() throws Exception {
        mockMvc.perform(get(ROWS)
                        .param("grain", "DEAL")
                        .param("from", BUCKET.minusDays(1).toString())
                        .param("to", BUCKET.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Заголовок контекста пуст — вопрос не принят")
    void aBlankTenantHeaderIsNotAccepted() throws Exception {
        mockMvc.perform(get(ROWS)
                        .header(TENANT_HEADER, "  ")
                        .param("grain", "DEAL")
                        .param("from", BUCKET.minusDays(1).toString())
                        .param("to", BUCKET.toString()))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return get(ROWS)
                .header(TENANT_HEADER, "tenant-1")
                .param("grain", "DEAL")
                .param("from", BUCKET.minusDays(1).toString())
                .param("to", BUCKET.toString());
    }

    private void given(AggregatePage page) {
        when(aggregateReadService.read(any())).thenReturn(page);
    }

    private DealAggregate dealRow() {
        return DealAggregate.builder()
                .tenantId("tenant-1")
                .exchangeAccountInternalId("acc-1")
                .strategyInternalId("strategy-1")
                .bucketDate(BUCKET)
                .resultCurrency("USDT")
                .closedDeals(4)
                .riskBearingDeals(3)
                .rSum(new BigDecimal("1.5"))
                .rDenominatorDeals(3)
                .assembledAt(MOMENT)
                .build();
    }

    /**
     * Минимальный контекст: контроллер, оба маппера и единый обработчик
     * ошибок. Выборка подменена — её поведение мерят свои тесты, а здесь
     * предмет — форма ответа.
     *
     * <p>Маппер журнала стои́т здесь не за компанию: форму полноты
     * агрегатный маппер берёт у него, а не описывает свою.
     */
    @Configuration
    @EnableWebMvc
    static class AggregateSurfaceConfig {

        @Bean
        AggregateReadService aggregateReadService() {
            return mock(AggregateReadService.class);
        }

        @Bean
        AggregateMapper aggregateMapper() {
            return new AggregateMapperImpl();
        }

        @Bean
        AggregateController aggregateController(AggregateReadService aggregateReadService,
                                                AggregateMapper aggregateMapper) {
            return new AggregateController(aggregateReadService, aggregateMapper);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
