package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.instrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.config.AnomalyReportProperties;
import com.example.tradingcore.domain.safety.AnomalyFinding;
import com.example.tradingcore.domain.safety.AnomalyReaction;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.persistence.service.AnomalyReportDataService;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Гистерезис: стоящая строка как носитель подтверждения — группа `U10`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/AnomalyJob.md §«Такт и гистерезис»).
 *
 * <p><b>Базовая сборка:</b> реакция на находку; служба отчётов
 * подменена и отвечает на вопрос «стои́т ли строка в окне»; сервис
 * журнала и сервис блокировки подменены. Находка собирается прямо:
 * радиус, ступень, код, инструмент, предмет, число тиков гистерезиса,
 * признак «только журнал».
 *
 * <p><b>Временем здесь не управляют:</b> границы окна читаются часами
 * процесса, и кейсы двигают возраст стоящей строки — то есть данные, —
 * а не часы.
 *
 * <p><b>`U10.17` здесь не прогоняется:</b> дом требует объявлять признак
 * «только журнал» явно и поведения на необъявленном не называет —
 * §«Кейсы, не прогоняемые сегодня» того же документа.
 */
class AnomalyHysteresisTest {

    private static final String SUBJECT = "ORD-91";

    private final AnomalyReportDataService reportDataService = mock(AnomalyReportDataService.class);
    private final AnomalyReportService reportService = mock(AnomalyReportService.class);
    private final HoldService holdService = mock(HoldService.class);
    private final AnomalyJobProperties jobProperties = new AnomalyJobProperties();
    private final AnomalyReportProperties reportProperties = new AnomalyReportProperties();

    private AnomalyReaction reaction;

    @BeforeEach
    void setUp() {
        reaction = new AnomalyReaction(reportDataService, reportService, holdService, jobProperties,
                reportProperties);
        standing(false);
    }

    private void standing(boolean answer) {
        when(reportDataService.existsStanding(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(answer);
    }

    private AnomalyFinding.AnomalyFindingBuilder finding() {
        return AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(CODE)
                .instrument(instrument())
                .hysteresisTicks(2)
                .journalOnly(false);
    }

    private HoldSignal raisedSignal() {
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(holdService).raise(captor.capture(), any());
        return captor.getValue();
    }

    private HoldSignal journalledSignal() {
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(reportService).journalState(any(), captor.capture(), any());
        return captor.getValue();
    }

    /** Гистерезиса нет — ступень поднимается сразу, и о стоящей строке никто не спрашивает. */
    @Test
    @DisplayName("U10.1 — гистерезис в один тик, признак «только журнал» ложен: ступень запрошена сразу")
    void u10_1_aFindingWithoutHysteresisReactsOnFirstSight() {
        reaction.apply(finding().hysteresisTicks(1).build(), account());

        verify(holdService).raise(any(), any());
        verify(reportDataService, never()).existsStanding(any(), any(), any(), any(), any(), any(), any());
    }

    /** Наблюдательная строка различает «ничего не нашли» и «нашли, ждём подтверждения». */
    @Test
    @DisplayName("U10.2 — гистерезис в два тика, стоящей строки нет: ступень не запрошена, пишется наблюдение")
    void u10_2_theFirstTickWritesAnObservationRow() {
        reaction.apply(finding().build(), account());

        verify(holdService, never()).raise(any(), any());
        verify(reportService).journalState(any(), any(), any());
    }

    /** Подтверждение получено — ступень запрошена сигналом находки. */
    @Test
    @DisplayName("U10.3 — строка стои́т в окне и старше минимального возраста: ступень запрошена, наблюдения нет")
    void u10_3_aConfirmedFindingRaisesTheRung() {
        standing(true);

        reaction.apply(finding().build(), account());

        verify(holdService).raise(any(), any());
        verify(reportService, never()).journalState(any(), any(), any());
    }

    /** Верхняя граница окна: строка, заведённая смежным проходом, подтверждением не служит. */
    @Test
    @DisplayName("U10.4 — строка моложе минимального возраста: пишется наблюдение, ступень не запрошена")
    void u10_4_theConfirmationWindowHasAnUpperMargin() {
        reaction.apply(finding().build(), account());

        ArgumentCaptor<OffsetDateTime> until = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(reportDataService).existsStanding(any(), any(), any(), any(), any(), any(),
                until.capture());
        assertThat(Duration.between(until.getValue(), OffsetDateTime.now()))
                .as("верхняя граница отстоит от момента на минимальный возраст подтверждения")
                .isGreaterThanOrEqualTo(jobProperties.getConfirmationMinAge());
        verify(holdService, never()).raise(any(), any());
        verify(reportService).journalState(any(), any(), any());
    }

    /** Нижняя граница окна отсекает отчёт давнего происхождения. */
    @Test
    @DisplayName("U10.5 — строка старше окна наблюдения: подтверждением не служит")
    void u10_5_theConfirmationWindowHasALowerBound() {
        reaction.apply(finding().build(), account());

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> until = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(reportDataService).existsStanding(any(), any(), any(), any(), any(),
                since.capture(), until.capture());
        assertThat(Duration.between(since.getValue(), until.getValue()))
                .as("между границами — окно наблюдения без минимального возраста")
                .isEqualTo(reportProperties.getObservationWindow()
                        .minus(jobProperties.getConfirmationMinAge()));
    }

    /** Гейт стои́т и перед журнальной находкой: тропы первого взгляда у неё нет. */
    @Test
    @DisplayName("U10.6 — журнальная находка, гистерезис в один тик: гейт стои́т и перед ней")
    void u10_6_theGateStandsBeforeAJournalFindingToo() {
        reaction.apply(finding().hysteresisTicks(1).journalOnly(true).build(), account());

        verify(reportDataService).existsStanding(any(), any(), any(), any(), any(), any(), any());
        verify(holdService, never()).raise(any(), any());
    }

    /** Держащееся состояние уже записано — второй строки не пишется. */
    @Test
    @DisplayName("U10.7 — журнальная находка, подтверждение получено: ступень не запрашивается, второй строки нет")
    void u10_7_aConfirmedJournalFindingWritesNothing() {
        standing(true);

        reaction.apply(finding().journalOnly(true).build(), account());

        verify(holdService, never()).raise(any(), any());
        verify(reportService, never()).journalState(any(), any(), any());
    }

    /** Без подтверждения журнальная находка пишет наблюдение. */
    @Test
    @DisplayName("U10.8 — журнальная находка, подтверждения нет: наблюдательная строка, блокировка не позвана")
    void u10_8_anUnconfirmedJournalFindingWritesAnObservationRow() {
        reaction.apply(finding().journalOnly(true).build(), account());

        verify(reportService).journalState(any(), any(), any());
        verify(holdService, never()).raise(any(), any());
    }

    /** Критичность производна от состава реакции, а не от того, какой она станет. */
    @Test
    @DisplayName("U10.9 — жёсткая находка, первый тик: наблюдательный сигнал журнальный, строка некритична")
    void u10_9_theObservationSignalIsAlwaysAJournalOne() {
        reaction.apply(finding().rung(HoldRung.HARD).build(), account());

        assertThat(journalledSignal().getRung())
                .as("на первом тике реакции нет")
                .isEqualTo(HoldRung.SOFT);
    }

    /** Радиус наблюдательного сигнала — радиус находки. */
    @Test
    @DisplayName("U10.10 — счётная находка берёт счётный радиус; инструментная — инструментный")
    void u10_10_theObservationSignalKeepsTheFindingScope() {
        reaction.apply(finding().scope(HoldScope.EXCHANGE_ACCOUNT).instrument(null).build(), account());

        assertThat(journalledSignal().getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);

        reaction.apply(finding().build(), account());
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(reportService, times(2)).journalState(any(), captor.capture(), any());
        assertThat(captor.getAllValues().get(1).getScope()).isEqualTo(HoldScope.INSTRUMENT);
    }

    /** Подтверждает строка первого тика, а она некритична всегда. */
    @Test
    @DisplayName("U10.11 — тик подтверждения: вопрос о стоящей строке задан с некритичной критичностью")
    void u10_11_theConfirmationQuestionAlwaysAsksForANonCriticalRow() {
        reaction.apply(finding().rung(HoldRung.HARD).build(), account());

        verify(reportDataService).existsStanding(any(), any(), any(), any(),
                eq(AnomalyReport.Severity.NON_CRITICAL), any(), any());
    }

    /** У счётной находки с предметом инструмента в ключе нет. */
    @Test
    @DisplayName("U10.12 — счётная находка с предметом и без инструмента: в ключе вопроса предмет, инструмента нет")
    void u10_12_anAccountFindingAsksWithoutAnInstrument() {
        reaction.apply(finding().scope(HoldScope.EXCHANGE_ACCOUNT).instrument(null)
                .subjectExternalId(SUBJECT).build(), account());

        verify(reportDataService).existsStanding(eq(ACCOUNT_ID), isNull(), eq(SUBJECT), eq(CODE),
                any(), any(), any());
    }

    /** У инструментной находки в ключе числовая идентичность инструмента. */
    @Test
    @DisplayName("U10.13 — инструментная находка: в ключе вопроса числовая идентичность инструмента")
    void u10_13_anInstrumentFindingAsksWithItsInstrumentId() {
        reaction.apply(finding().build(), account());

        verify(reportDataService).existsStanding(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), isNull(), eq(CODE),
                any(), any(), any());
    }

    /** Отказ журнального носителя реакцию не гейтит. */
    @Test
    @DisplayName("U10.14 — запись наблюдательной строки бросает: исключения наружу нет, в логе запись")
    void u10_14_aFailingObservationWriteIsSwallowed() {
        when(reportService.journalState(any(), any(), any()))
                .thenThrow(new IllegalStateException("db is down"));

        try (SafetyLogCapture log = SafetyLogCapture.attach(AnomalyReaction.class)) {
            assertThatCode(() -> reaction.apply(finding().build(), account()))
                    .doesNotThrowAnyException();

            assertThat(log.messages())
                    .anyMatch(message -> message.contains("Anomaly observation row is not written"));
        }
    }

    /** Подтверждённая находка счётного радиуса берёт фабрику своей ступени. */
    @Test
    @DisplayName("U10.15 — подтверждённая жёсткая находка счёта даёт жёсткую счётную фабрику, мягкая — мягкую")
    void u10_15_theConfirmedAccountFindingPicksItsFactory() {
        standing(true);

        reaction.apply(finding().scope(HoldScope.EXCHANGE_ACCOUNT).instrument(null)
                .rung(HoldRung.HARD).build(), account());

        assertThat(raisedSignal()).isEqualTo(HoldSignal.exchangeAccount(CODE));

        reaction.apply(finding().scope(HoldScope.EXCHANGE_ACCOUNT).instrument(null).build(), account());
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(holdService, times(2)).raise(captor.capture(), any());
        assertThat(captor.getAllValues().get(1)).isEqualTo(HoldSignal.exchangeAccountSoft(CODE));
    }

    /** То же у инструментного радиуса. */
    @Test
    @DisplayName("U10.16 — подтверждённая жёсткая находка инструмента даёт жёсткую инструментную фабрику, мягкая — мягкую")
    void u10_16_theConfirmedInstrumentFindingPicksItsFactory() {
        standing(true);

        reaction.apply(finding().rung(HoldRung.HARD).build(), account());

        assertThat(raisedSignal()).isEqualTo(HoldSignal.instrument(CODE));

        reaction.apply(finding().build(), account());
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(holdService, times(2)).raise(captor.capture(), any());
        assertThat(captor.getAllValues().get(1)).isEqualTo(HoldSignal.instrumentSoft(CODE));
    }
}
