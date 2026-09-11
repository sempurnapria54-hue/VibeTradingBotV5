package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.AggregateReadProperties;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.model.AggregateGrain;
import com.example.auditstatistics.domain.model.AggregatePage;
import com.example.auditstatistics.domain.model.AggregateQuery;
import com.example.auditstatistics.domain.model.DealAggregate;
import com.example.auditstatistics.domain.model.IncidentAggregate;
import com.example.auditstatistics.domain.model.JournalCompleteness;
import com.example.auditstatistics.domain.service.AggregateReadService;
import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.persistence.service.DealAggregateDataService;
import com.example.auditstatistics.persistence.service.IncidentAggregateDataService;
import com.example.auditstatistics.persistence.service.StatisticsJournalCompletenessSource;
import java.time.Duration;
import java.time.LocalDate;
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
 * Курсорная страница агрегатной выборки: сколько строк читается, что
 * отдаётся, чем отвечается «окно дочитано» и чем полнота
 * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
 * читает»).
 *
 * <p><b>Продолжение узнаётся ЛИШНЕЙ прочитанной строкой.</b> Полная
 * страница сама по себе о продолжении не говорит: окно, чей остаток равен
 * размеру страницы ровно, обещало бы читателю ещё один — пустой — запрос.
 *
 * <p><b>Позиция продолжения — КЛЮЧ ЗЕРНА, и у двух зёрен он разный.</b>
 * Проба на зерне происшествий смотрит, что двух хвостовых компонентов у
 * позиции нет: взятые «на всякий случай», они означали бы строку с пустым
 * ключом, которой в этом зерне не бывает.
 *
 * <p><b>Перечень чужого зерна остаётся ПУСТЫМ, а не пустым перечнем.</b>
 * Пустой перечень означает «зерно выбрано, строк нет», и сведённые в одно
 * эти состояния дали бы пустоте два смысла.
 */
class AggregateReadPageTest {

    private static final String TENANT = "tenant-1";
    private static final String GROUP = "audit-statistics.journal";
    private static final Integer PAGE_SIZE = 3;
    private static final Duration STATE_MAX_AGE = Duration.ofMinutes(5);
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final OffsetDateTime MOMENT =
            OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    private final DealAggregateDataService dealAggregateDataService = mock(DealAggregateDataService.class);
    private final IncidentAggregateDataService incidentAggregateDataService =
            mock(IncidentAggregateDataService.class);
    private final JournalCompletenessService completenessService = mock(JournalCompletenessService.class);
    private final StatisticsJournalCompletenessSource completenessSource =
            mock(StatisticsJournalCompletenessSource.class);
    private final AggregateReadProperties aggregateReadProperties = new AggregateReadProperties();
    private final ReceptionProperties receptionProperties = new ReceptionProperties();

    private AggregateReadService service;

    @BeforeEach
    void setUp() {
        aggregateReadProperties.setMaxWindowDays(30);
        aggregateReadProperties.setPageSize(PAGE_SIZE);
        receptionProperties.setGroupId(GROUP);
        receptionProperties.setStateMaxAge(STATE_MAX_AGE);
        service = new AggregateReadService(aggregateReadProperties, receptionProperties,
                dealAggregateDataService, incidentAggregateDataService,
                completenessService, completenessSource);
        when(dealAggregateDataService.findPage(any(), anyInt())).thenReturn(List.of());
        when(incidentAggregateDataService.findPage(any(), anyInt())).thenReturn(List.of());
        when(completenessService.completeness(any(), anyString(), any()))
                .thenReturn(new JournalCompleteness(MOMENT, Boolean.TRUE));
    }

    @Test
    @DisplayName("Читается на одну строку больше страницы — ею и узнаётся продолжение")
    void oneRowBeyondThePageIsRead() {
        service.read(dealQuery());

        verify(dealAggregateDataService).findPage(any(), eq(PAGE_SIZE + 1));
    }

    @Test
    @DisplayName("Строк больше страницы: отдаётся страница, а лишняя остаётся внутри")
    void theExtraRowIsNotHandedOut() {
        givenDealRows(PAGE_SIZE + 1);

        AggregatePage page = service.read(dealQuery());

        assertThat(page.getDealRows())
                .as("наружу уходит страница, а не всё прочитанное")
                .hasSize(PAGE_SIZE)
                .extracting(DealAggregate::getExchangeAccountInternalId)
                .containsExactly("acc-0", "acc-1", "acc-2");
    }

    @Test
    @DisplayName("Позиция продолжения — ключ последней ОТДАННОЙ строки, а не последней прочитанной")
    void theCursorPointsAtTheLastHandedRow() {
        givenDealRows(PAGE_SIZE + 1);

        assertThat(service.read(dealQuery()).getNextCursor())
                .as("позиция от лишней строки пропустила бы последнюю строку страницы")
                .satisfies(cursor -> {
                    assertThat(cursor.getExchangeAccountInternalId()).isEqualTo("acc-2");
                    assertThat(cursor.getBucketDate()).isEqualTo(FROM.plusDays(2));
                    assertThat(cursor.getStrategyInternalId()).isEqualTo("strategy-2");
                    assertThat(cursor.getResultCurrency()).isEqualTo("USDT");
                });
    }

    @Test
    @DisplayName("Строк ровно на страницу — окно дочитано, позиции продолжения нет")
    void aFullPageWithoutAnExtraRowEndsTheWindow() {
        givenDealRows(PAGE_SIZE);

        AggregatePage page = service.read(dealQuery());

        assertThat(page.getDealRows()).hasSize(PAGE_SIZE);
        assertThat(page.getNextCursor())
                .as("полная страница сама по себе о продолжении не говорит")
                .isNull();
    }

    @Test
    @DisplayName("Строк нет вовсе — перечень пуст, позиции нет")
    void anEmptyWindowHandsAnEmptyList() {
        AggregatePage page = service.read(dealQuery());

        assertThat(page.getDealRows()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("Перечень чужого зерна остаётся ПУСТЫМ, а не пустым перечнем")
    void theForeignGrainListStaysAbsent() {
        givenDealRows(1);

        AggregatePage dealPage = service.read(dealQuery());

        assertThat(dealPage.getIncidentRows())
                .as("пустой перечень означал бы «строк нет», а спрошено другое зерно")
                .isNull();
        assertThat(dealPage.getGrain()).isEqualTo(AggregateGrain.DEAL);

        givenIncidentRows(1);

        AggregatePage incidentPage = service.read(incidentQuery());

        assertThat(incidentPage.getDealRows()).isNull();
        assertThat(incidentPage.getIncidentRows()).hasSize(1);
        assertThat(incidentPage.getGrain()).isEqualTo(AggregateGrain.INCIDENT);
    }

    @Test
    @DisplayName("Позиция зерна происшествий несёт два компонента: пустых колонок в его ключе нет")
    void theIncidentCursorCarriesTwoComponents() {
        givenIncidentRows(PAGE_SIZE + 1);

        assertThat(service.read(incidentQuery()).getNextCursor())
                .satisfies(cursor -> {
                    assertThat(cursor.getBucketDate()).isEqualTo(FROM.plusDays(2));
                    assertThat(cursor.getExchangeAccountInternalId()).isEqualTo("acc-2");
                    assertThat(cursor.getStrategyInternalId())
                            .as("компонента, которого нет в ключе зерна, у позиции быть не может")
                            .isNull();
                    assertThat(cursor.getResultCurrency()).isNull();
                });
    }

    @Test
    @DisplayName("Размер страницы приходит из конфигурации, а не из константы кода")
    void thePageSizeComesFromTheConfiguration() {
        aggregateReadProperties.setPageSize(7);

        service.read(dealQuery());

        verify(dealAggregateDataService).findPage(any(), eq(8));
    }

    /**
     * Полнота едет тем же ответом и берётся источником АГРЕГАТНОГО
     * подключения: журнал для модуля статистики — чужая база.
     */
    @Test
    @DisplayName("Полнота спрашивается источником агрегатного подключения и едет вместе со строками")
    void completenessComesFromTheStatisticsSource() {
        givenDealRows(1);

        AggregatePage page = service.read(dealQuery());

        assertThat(page.getCompleteness().getLowerBound()).isEqualTo(MOMENT);
        assertThat(page.getCompleteness().getContinuityClaimable()).isTrue();
        verify(completenessService).completeness(eq(completenessSource), eq(GROUP), any());
    }

    @Test
    @DisplayName("Момент устаревания — момент выдачи за вычетом допустимого возраста")
    void theStaleMomentSubtractsTheAllowedAge() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        service.read(dealQuery());

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
        receptionProperties.setGroupId("another-group");

        service.read(dealQuery());

        verify(completenessService).completeness(any(), eq("another-group"), any());
    }

    private void givenDealRows(Integer count) {
        List<DealAggregate> rows = new ArrayList<>();
        IntStream.range(0, count).forEach(index -> rows.add(DealAggregate.builder()
                .tenantId(TENANT)
                .exchangeAccountInternalId("acc-" + index)
                .strategyInternalId("strategy-" + index)
                .bucketDate(FROM.plusDays(index))
                .resultCurrency("USDT")
                .closedDeals(index)
                .build()));
        when(dealAggregateDataService.findPage(any(), anyInt())).thenReturn(rows);
    }

    private void givenIncidentRows(Integer count) {
        List<IncidentAggregate> rows = new ArrayList<>();
        IntStream.range(0, count).forEach(index -> rows.add(IncidentAggregate.builder()
                .tenantId(TENANT)
                .exchangeAccountInternalId("acc-" + index)
                .bucketDate(FROM.plusDays(index))
                .openedDeals(index)
                .build()));
        when(incidentAggregateDataService.findPage(any(), anyInt())).thenReturn(rows);
    }

    private AggregateQuery dealQuery() {
        return query(AggregateGrain.DEAL);
    }

    private AggregateQuery incidentQuery() {
        return query(AggregateGrain.INCIDENT);
    }

    private AggregateQuery query(AggregateGrain grain) {
        return AggregateQuery.builder()
                .tenantId(TENANT)
                .grain(grain)
                .from(FROM)
                .to(FROM.plusDays(5))
                .build();
    }
}
