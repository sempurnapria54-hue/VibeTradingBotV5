package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACTOR;
import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_EXTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.TENANT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.accountContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.dealWithLiveRisk;
import static com.example.tradingcore.unit.safety.SafetyFixture.instrument;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.security.ActorProvider;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.config.AnomalyReportProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.AnomalyReportDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Ключ дедупа факта-состояния и две природы факта — группа `U9`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом ключа — docs/models/domain/other/AnomalyReport.md §«Ключ дедупа
 * у состояния»; природа факта — §«Природа факта — свойство тропы, а не
 * колонка» того же дока).
 *
 * <p><b>Базовая сборка:</b> сервис журнала; служба отчётов подменена и
 * отвечает на вопрос «стои́т ли строка по ключу»; клиент площадки отдаёт
 * позицию и живые заявки; писатель фактов принимает вызов; контекст
 * несёт счёт и инструмент.
 *
 * <p><b>Сериализатор настоящий</b>: снимок есть выход предмета, и
 * подменённый сериализатор проверял бы подмену. Подменяется он ровно в
 * той клетке, чей предмет — его отказ (`U9.15`).
 */
class AnomalyReportDedupTest {

    private static final String SUBJECT = "ORD-77";

    private final AnomalyReportDataService dataService = mock(AnomalyReportDataService.class);
    private final ExchangeOperationsClient exchangeClient = mock(ExchangeOperationsClient.class);
    private final ActorProvider actorProvider = mock(ActorProvider.class);
    private final CoreEventWriter coreEventWriter = mock(CoreEventWriter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnomalyReportProperties properties = new AnomalyReportProperties();

    private AnomalyReportService service;

    @BeforeEach
    void setUp() {
        service = new AnomalyReportService(dataService, exchangeClient, objectMapper, properties,
                actorProvider, coreEventWriter);
        when(dataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dataService.existsStanding(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(actorProvider.currentActor()).thenReturn(ACTOR);
    }

    private AnomalyReport saved() {
        ArgumentCaptor<AnomalyReport> captor = ArgumentCaptor.forClass(AnomalyReport.class);
        verify(dataService).save(captor.capture());
        return captor.getValue();
    }

    /** Журнальная тропа создаёт отчёт уже завершённым: после-снимков у него нет. */
    @Test
    @DisplayName("U9.1 — писатель состояния, строки по ключу нет: строка заведена завершённой, факт опубликован")
    void u9_1_aStateRowIsCreatedCompletedAndPublished() {
        AnomalyReport returned = service.journalState(pairContext(), HoldSignal.instrumentSoft(CODE), null);

        assertThat(returned).as("возврат — сама строка").isNotNull();
        assertThat(saved().getStatus()).isEqualTo(AnomalyReport.Status.COMPLETED);
        verify(coreEventWriter).anomalyReported(eq(TENANT_ID), any(), eq(ACCOUNT_INTERNAL_ID),
                eq(INSTRUMENT_INTERNAL_ID), eq(ACTOR));
    }

    /** Строка по ключу стои́т — второй нет, и события тоже: ход идёт из создания. */
    @Test
    @DisplayName("U9.2 — писатель состояния, строка по ключу стои́т: второй строки нет, факт не опубликован, возврат пуст")
    void u9_2_aStandingRowAbsorbsTheStateWrite() {
        when(dataService.existsStanding(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        AnomalyReport returned = service.journalState(pairContext(), HoldSignal.instrumentSoft(CODE), null);

        assertThat(returned).isNull();
        verify(dataService, never()).save(any());
        verify(coreEventWriter, never()).anomalyReported(any(), any(), any(), any(), any());
    }

    /** Код — величина ключа: другой код не совпал, и заводится своя строка. */
    @Test
    @DisplayName("U9.3 — стоящая строка того же объекта с другим кодом: ключ не совпал, своя строка")
    void u9_3_aDifferentCodeIsADifferentKey() {
        when(dataService.existsStanding(any(), any(), any(), eq("OTHER_REASON"), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.journalState(pairContext(), HoldSignal.instrumentSoft(CODE), null)).isNotNull();
        verify(dataService).existsStanding(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), isNull(), eq(CODE),
                eq(AnomalyReport.Severity.NON_CRITICAL), any(), any());
    }

    /** Критичность — величина ключа: эскалация мягкой в полную заводит свою строку. */
    @Test
    @DisplayName("U9.4 — стоящая строка некритична, а сигнал жёсткий: ключ не совпал по критичности")
    void u9_4_severityIsPartOfTheKey() {
        when(dataService.existsStanding(any(), any(), any(), any(),
                eq(AnomalyReport.Severity.NON_CRITICAL), any(), any())).thenReturn(true);

        AnomalyReport returned = service.journalState(pairContext(), HoldSignal.instrument(CODE), null);

        assertThat(returned).isNotNull();
        verify(dataService).existsStanding(any(), any(), any(), any(),
                eq(AnomalyReport.Severity.CRITICAL), any(), any());
        assertThat(saved().getSeverity()).isEqualTo(AnomalyReport.Severity.CRITICAL);
    }

    /** Объект радиуса — величина ключа. */
    @Test
    @DisplayName("U9.5 — стоящая строка на другом биржевом счёте: ключ не совпал по объекту радиуса")
    void u9_5_theScopeObjectIsPartOfTheKey() {
        when(dataService.existsStanding(eq(ACCOUNT_ID + 1), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.journalState(pairContext(), HoldSignal.instrumentSoft(CODE), null)).isNotNull();
        verify(dataService).existsStanding(eq(ACCOUNT_ID), any(), any(), any(), any(), any(), any());
    }

    /** У отчёта без блокировки в ключ входит сущность-предмет. */
    @Test
    @DisplayName("U9.6 — два расхождения по одному инструменту с разными предметами: две строки")
    void u9_6_theSubjectIsPartOfTheKeyOfANonBlockingReport() {
        when(dataService.existsStanding(any(), any(), eq(SUBJECT), any(), any(), any(), any()))
                .thenReturn(true);

        AnomalyReport other = service.journalState(pairContext(), HoldSignal.instrumentJournal(CODE),
                "ORD-88");

        assertThat(other).isNotNull();
        assertThat(service.journalState(pairContext(), HoldSignal.instrumentJournal(CODE), SUBJECT))
                .as("строка того же предмета поглощается дедупом")
                .isNull();
        verify(dataService).existsStanding(any(), any(), eq("ORD-88"), any(), any(), any(), any());
    }

    /** Нижняя граница окна: отчёт давнего происхождения подтверждением не служит. */
    @Test
    @DisplayName("U9.7 — стоящая строка старше окна наблюдения: подтверждением не служит")
    void u9_7_theObservationWindowHasALowerBound() {
        service.journalState(pairContext(), HoldSignal.instrumentSoft(CODE), null);

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> until = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(dataService).existsStanding(any(), any(), any(), any(), any(),
                since.capture(), until.capture());
        assertThat(java.time.Duration.between(since.getValue(), until.getValue()))
                .as("нижняя граница отстоит от верхней ровно на окно наблюдения")
                .isEqualTo(properties.getObservationWindow());
    }

    /** У окна дедупа верхней границы нет, в отличие от окна подтверждения гистерезиса. */
    @Test
    @DisplayName("U9.8 — стоящая строка заведена секунду назад: дедуп срабатывает, верхней границы у окна нет")
    void u9_8_theDedupWindowHasNoUpperMargin() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        service.journalState(pairContext(), HoldSignal.instrumentSoft(CODE), null);

        ArgumentCaptor<OffsetDateTime> until = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(dataService).existsStanding(any(), any(), any(), any(), any(), any(), until.capture());
        assertThat(until.getValue())
                .as("верхняя граница — сам момент вопроса")
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Происшествия обязаны быть счётными: дедупа у них нет. */
    @Test
    @DisplayName("U9.9 — писатель происшествия при стоящей строке того же ключа: заводится вторая строка")
    void u9_9_anIncidentWriterHasNoDedup() {
        when(dataService.existsStanding(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.journal(pairContext(), HoldSignal.instrumentJournal(CODE))).isNotNull();
        verify(dataService, never()).existsStanding(any(), any(), any(), any(), any(), any(), any());
    }

    /** У критичной тропы дедупа нет: снятие риска есть происшествие своего момента. */
    @Test
    @DisplayName("U9.10 — открытие отчёта критичной тропы при стоящей строке: строка заводится")
    void u9_10_theCriticalPathHasNoDedup() {
        when(dataService.existsStanding(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        assertThat(service.open(pairContext(), HoldSignal.instrument(CODE))).isNotNull();
        assertThat(saved().getStatus()).isEqualTo(AnomalyReport.Status.CREATED);
        verify(dataService, never()).existsStanding(any(), any(), any(), any(), any(), any(), any());
    }

    /** Критичность производна от ступени сигнала. */
    @Test
    @DisplayName("U9.11 — жёсткий сигнал даёт критичную строку; мягкий и журнальный — некритичную")
    void u9_11_severityFollowsTheSignalRung() {
        service.journal(pairContext(), HoldSignal.instrument(CODE));
        service.journal(pairContext(), HoldSignal.instrumentSoft(CODE));
        service.journal(pairContext(), HoldSignal.instrumentJournal(CODE));

        ArgumentCaptor<AnomalyReport> captor = ArgumentCaptor.forClass(AnomalyReport.class);
        verify(dataService, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AnomalyReport::getSeverity)
                .containsExactly(AnomalyReport.Severity.CRITICAL,
                        AnomalyReport.Severity.NON_CRITICAL,
                        AnomalyReport.Severity.NON_CRITICAL);
    }

    /** Снимок «до» несёт то, что известно проходу. */
    @Test
    @DisplayName("U9.12 — контекст со сделкой и инструментом: локальный снимок несёт идентичности, статус, риск, ноги и поля объектов")
    void u9_12_theLocalSnapshotCarriesWhatThePassKnows() throws Exception {
        Deal deal = dealWithLiveRisk(61L);
        deal.setTranches(new ArrayList<>(List.of(tranche(1L, 101L, 102L))));

        service.open(context(deal), HoldSignal.instrument(CODE));

        Map<String, Object> snapshot = snapshotOf(saved().getInternalBefore());
        assertThat(snapshot).containsEntry("dealId", 61)
                .containsEntry("dealInternalId", "DEA-61")
                .containsEntry("dealStatus", "ACTIVE")
                .containsEntry("positionLiveRisk", true)
                .containsEntry("orderIds", List.of(101, 102))
                .containsEntry("instrumentId", INSTRUMENT_ID.intValue())
                .containsEntry("instrumentExternalId", INSTRUMENT_EXTERNAL_ID)
                .containsEntry("exchangeAccountId", ACCOUNT_ID.intValue())
                .containsEntry("exchangeAccountInternalId", ACCOUNT_INTERNAL_ID);
    }

    /** У счёт-широкой тропы добывать нечего, и отчёт не роняется. */
    @Test
    @DisplayName("U9.13 — контекст без сделки и без инструмента: локальный снимок несёт только поля счёта, внешнего нет")
    void u9_13_theAccountWidePathHasNoExternalSnapshot() throws Exception {
        service.open(accountContext(), HoldSignal.exchangeAccount(CODE));

        AnomalyReport report = saved();
        Map<String, Object> snapshot = snapshotOf(report.getInternalBefore());
        assertThat(snapshot.keySet())
                .containsExactly("exchangeAccountId", "exchangeAccountInternalId",
                        "exchangeAccountSafetyRung");
        assertThat(report.getExternalBefore()).as("добывать нечего").isNull();
    }

    /** Чтение площадки best-effort: маркер отказа остаётся в снимке. */
    @Test
    @DisplayName("U9.14 — чтение площадки бросает: отчёт заведён, на месте недобытого маркер отказа")
    void u9_14_aFailingExchangeReadLeavesAMarker() throws Exception {
        when(exchangeClient.getPosition(any(), any()))
                .thenThrow(new IllegalStateException("exchange is down"));

        service.open(pairContext(), HoldSignal.instrument(CODE));

        Map<String, Object> snapshot = snapshotOf(saved().getExternalBefore());
        assertThat(snapshot.get("position")).isEqualTo(Map.of("readError", "IllegalStateException"));
    }

    /** Сериализация best-effort: её сбой отчёт тоже не валит. */
    @Test
    @DisplayName("U9.15 — сериализация снимка бросает: отчёт заведён, снимок пуст, исключения наружу нет")
    void u9_15_aFailingSerializationDoesNotFailTheReport() throws JsonProcessingException {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new FailingJson("snapshot is not writable"));
        AnomalyReportService withFailingMapper = new AnomalyReportService(dataService, exchangeClient,
                failing, properties, actorProvider, coreEventWriter);

        assertThatCode(() -> withFailingMapper.open(pairContext(), HoldSignal.instrument(CODE)))
                .doesNotThrowAnyException();

        assertThat(saved().getInternalBefore()).isNull();
    }

    /** Остаточный риск частичного снятия виден именно в снимке «после». */
    @Test
    @DisplayName("U9.16 — терминал критичной тропы: снимки «после» собраны, статус завершён")
    void u9_16_theTerminalCollectsTheAfterSnapshots() {
        AnomalyReport report = CoordinatorHarness.report(602L);

        service.complete(report, pairContext());

        assertThat(report.getStatus()).isEqualTo(AnomalyReport.Status.COMPLETED);
        assertThat(report.getInternalAfter()).isNotNull();
        assertThat(report.getExternalAfter()).isNotNull();
        verify(exchangeClient).getPosition(ACCOUNT_INTERNAL_ID, INSTRUMENT_EXTERNAL_ID);
    }

    /** Текст ошибки усечён под предел колонки. */
    @Test
    @DisplayName("U9.17 — запись ошибки длиннее предела колонки: текст усечён, статус — ошибка обработки")
    void u9_17_theErrorMessageIsAbbreviatedToTheColumnLimit() {
        AnomalyReport report = CoordinatorHarness.report(603L);

        service.fail(report, "x".repeat(4096));

        assertThat(report.getMessage()).hasSize(1024).endsWith("...");
        assertThat(report.getStatus()).isEqualTo(AnomalyReport.Status.ERROR);
    }

    /** Ноги берутся обходом траншей — донорского поля агрегата ядро не читает. */
    @Test
    @DisplayName("U9.18 — ноги лежат по траншам: перечень ног снимка собран их обходом")
    void u9_18_theLegsAreCollectedByWalkingTheTranches() throws Exception {
        Deal deal = dealWithLiveRisk(62L);
        deal.setTranches(new ArrayList<>(List.of(tranche(1L, 201L), tranche(2L, 202L, 203L))));

        service.open(context(deal), HoldSignal.instrument(CODE));

        Map<String, Object> snapshot = snapshotOf(saved().getInternalBefore());
        assertThat(snapshot).containsEntry("orderIds", List.of(201, 202, 203));
    }

    /**
     * У отчёта С блокировкой состояние и есть стоящая ступень объекта
     * радиуса, поэтому величины предмета у него нет — ни в строке, ни в
     * ключе. Отрицательная сторона того же предиката, чью положительную
     * берёт `U9.6`.
     *
     * <p>Строка добрана под-шагом 3 по пробелу `G2`: группа выведена от
     * ключа дедупа, и отрицательная сторона в неё не попала.
     */
    @Test
    @DisplayName("U9.19 — отчёт с блокировкой: величина предмета пуста, и ключ её не несёт")
    void u9_19_aBlockingReportCarriesNoSubject() {
        service.journalState(pairContext(), HoldSignal.instrumentSoft(CODE), null);

        verify(dataService).existsStanding(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), isNull(), eq(CODE),
                eq(AnomalyReport.Severity.NON_CRITICAL), any(), any());
        assertThat(saved().getSubjectExternalId())
                .as("у отчёта с блокировкой сущности-предмета нет")
                .isNull();
    }

    /** Происшествие, чей момент задан предметом: строка с предметом и операндами решения в снимке «до». */
    @Test
    @DisplayName("U9.20 — писатель происшествия по предмету, отчёта по предмету нет: строка с операндами")
    void u9_20_aSubjectIncidentCarriesItsOperands() throws JsonProcessingException {
        when(dataService.existsForSubject(CODE, SUBJECT)).thenReturn(false);

        AnomalyReport returned = service.journalOnce(pairContext(), HoldSignal.instrumentJournal(CODE), SUBJECT,
                Map.of("exitRounding", Map.of("exitOutcome", "FULL")));

        assertThat(returned).isNotNull();
        AnomalyReport row = saved();
        assertThat(row.getSubjectExternalId()).isEqualTo(SUBJECT);
        assertThat(row.getStatus()).isEqualTo(AnomalyReport.Status.COMPLETED);
        assertThat(snapshotOf(row.getInternalBefore()))
                .containsEntry("exitRounding", Map.of("exitOutcome", "FULL"))
                .containsKey("instrumentId");
        verify(coreEventWriter).anomalyReported(eq(TENANT_ID), any(), eq(ACCOUNT_INTERNAL_ID),
                eq(INSTRUMENT_INTERNAL_ID), eq(ACTOR));
    }

    /** Одно решение по предмету — один отчёт: повторная запись поглощается без окна. */
    @Test
    @DisplayName("U9.21 — отчёт по предмету уже заведён: второй строки нет, факт не опубликован, возврат пуст")
    void u9_21_aSecondIncidentOfTheSameSubjectIsAbsorbed() {
        when(dataService.existsForSubject(CODE, SUBJECT)).thenReturn(true);

        AnomalyReport returned = service.journalOnce(pairContext(), HoldSignal.instrumentJournal(CODE), SUBJECT,
                Map.of());

        assertThat(returned).isNull();
        verify(dataService, never()).save(any());
        verify(coreEventWriter, never()).anomalyReported(any(), any(), any(), any(), any());
        verify(dataService, never()).existsStanding(any(), any(), any(), any(), any(), any(), any());
    }

    /** Код — величина ключа и здесь: другой код по тому же предмету заводит свою строку. */
    @Test
    @DisplayName("U9.22 — по предмету заведён отчёт с другим кодом: ключ не совпал, своя строка")
    void u9_22_aDifferentCodeOfTheSameSubjectIsItsOwnRow() {
        when(dataService.existsForSubject("OTHER_REASON", SUBJECT)).thenReturn(true);
        when(dataService.existsForSubject(CODE, SUBJECT)).thenReturn(false);

        assertThat(service.journalOnce(pairContext(), HoldSignal.instrumentJournal(CODE), SUBJECT, Map.of()))
                .isNotNull();
    }

    private Map<String, Object> snapshotOf(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
    }

    private DealContext context(Deal deal) {
        return DealContext.builder().deal(deal).exchangeAccount(account()).instrument(instrument())
                .build();
    }

    /** Отказ сериализатора своего класса не имеет — подставляем проверяемый. */
    private static final class FailingJson extends JsonProcessingException {

        private FailingJson(String message) {
            super(message);
        }
    }
}
