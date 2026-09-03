package com.example.tradingbot.domain.safety;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.config.AnomalyJobProperties;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import com.example.tradingbot.util.Constants;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает реакцию на находку с домом дедупа и гистерезиса
 * (docs/rules/error-handling-policy.md §«Состояние «держится» читается по
 * объекту, а не по статусу отчёта», docs/components/AnomalyJob.md §«Такт
 * и гистерезис»).
 *
 * <p>Несущее для этого теста — <b>что строка одна, пока состояние
 * держится</b>. Журнальная находка без гейта заводила бы строку каждым
 * тиком бессрочно: одна залипшая сущность давала бы полторы тысячи строк
 * в сутки, и журнал разбора, ради которого отчёт и существует,
 * превращался бы в шум.
 *
 * <p><b>Второе несущее — что окно подтверждения имеет ВЕРХНЮЮ границу.</b>
 * Гистерезис заведён против гонки чтения длиной в такт; пара проходов,
 * идущих подряд в секундах (ручной триггер поверх планового тика), её не
 * переживает, а без верхней границы формально «подтверждает» транзиторное
 * расхождение — то есть поднимает жёсткую ступень со сносом по рынку.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyReactionTest {

    private static final Long EXCHANGE_ID = 1L;
    private static final Long INSTRUMENT_ID = 7L;

    @Mock
    private AnomalyReportDataService reportDataService;

    @Mock
    private AnomalyReportService reportService;

    @Mock
    private HoldService holdService;

    private final AnomalyJobProperties properties = new AnomalyJobProperties();

    private AnomalyReaction reaction;

    @BeforeEach
    void setUp() {
        reaction = new AnomalyReaction(reportDataService, reportService, holdService, properties);
    }

    @Test
    @DisplayName("Журнальная находка заводит строку, пока по ключу не стои́т своя")
    void journalOnlyFindingWritesObservationRow() {
        standingRow(false);

        reaction.apply(journalOnly(), exchange());

        verify(reportService).journal(any(), any(), any());
        verify(holdService, never()).raise(any(), any());
    }

    @Test
    @DisplayName("Журнальная находка ВТОРОЙ строки не заводит: строка одна, пока состояние держится")
    void journalOnlyFindingDoesNotDuplicateStandingRow() {
        standingRow(true);

        reaction.apply(journalOnly(), exchange());

        verify(reportService, never()).journal(any(), any(), any());
        verify(holdService, never()).raise(any(), any());
    }

    @Test
    @DisplayName("Гистерезис два тика: первое наблюдение ступени не поднимает")
    void firstObservationDoesNotRaiseRung() {
        standingRow(false);

        reaction.apply(withHysteresis(), exchange());

        verify(reportService).journal(any(), any(), any());
        verify(holdService, never()).raise(any(), any());
    }

    @Test
    @DisplayName("Подтверждённый признак поднимает ступень и второй строки не заводит")
    void confirmedFindingRaisesRung() {
        standingRow(true);

        reaction.apply(withHysteresis(), exchange());

        verify(holdService).raise(any(), any());
        verify(reportService, never()).journal(any(), any(), any());
    }

    @Test
    @DisplayName("Находка без гистерезиса поднимает ступень с первого наблюдения")
    void findingWithoutHysteresisRaisesOnFirstSight() {
        reaction.apply(withoutHysteresis(), exchange());

        verify(holdService).raise(any(), any());
        verify(reportDataService, never()).existsStanding(anyLong(), any(), any(), anyString(), any(),
                any(), any());
    }

    @Test
    @DisplayName("У окна подтверждения ДВЕ границы: строка этого же прохода подтверждением не служит")
    void confirmationWindowHasUpperBound() {
        standingRow(true);

        reaction.apply(withHysteresis(), exchange());

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> until = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(reportDataService).existsStanding(eq(EXCHANGE_ID), eq(INSTRUMENT_ID), any(), anyString(),
                any(), since.capture(), until.capture());

        assertTrue(until.getValue().isAfter(since.getValue()),
                "верхняя граница окна обязана быть позже нижней");
        Duration ageAtUpperBound = Duration.between(until.getValue(), OffsetDateTime.now());
        assertTrue(ageAtUpperBound.compareTo(properties.getConfirmationMinAge().minusSeconds(1)) >= 0,
                "подтверждающая строка обязана быть старше confirmationMinAge");
    }

    @Test
    @DisplayName("Ключ подтверждения — некритичная наблюдательная строка, а не эскалация")
    void confirmationReadsNonCriticalRow() {
        standingRow(true);

        reaction.apply(withHysteresis(), exchange());

        verify(reportDataService).existsStanding(anyLong(), any(), any(), anyString(),
                eq(AnomalyReport.Severity.NON_CRITICAL), any(), any());
    }

    @Test
    @DisplayName("Сбой записи журнальной строки реакцию не валит: ограничение риска приоритетнее журнала")
    void journalFailureDoesNotBreakThePass() {
        standingRow(false);
        when(reportService.journal(any(), any(), any())).thenThrow(new IllegalStateException("БД недоступна"));

        assertDoesNotThrow(() -> reaction.apply(journalOnly(), exchange()));
    }

    private void standingRow(Boolean standing) {
        when(reportDataService.existsStanding(anyLong(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(standing);
    }

    private AnomalyFinding journalOnly() {
        return finding(2, true);
    }

    private AnomalyFinding withHysteresis() {
        return finding(2, false);
    }

    private AnomalyFinding withoutHysteresis() {
        return finding(1, false);
    }

    private AnomalyFinding finding(Integer hysteresisTicks, Boolean journalOnly) {
        return AnomalyFinding.builder()
                .scope(HoldScope.INSTRUMENT)
                .rung(HoldRung.SOFT)
                .code(Constants.Hold.INSTRUMENT_ORPHAN_ORDERS)
                .instrument(instrument())
                .hysteresisTicks(hysteresisTicks)
                .journalOnly(journalOnly)
                .build();
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("BTC-USDT-SWAP");
        return instrument;
    }

    private Exchange exchange() {
        Exchange exchange = new Exchange();
        exchange.setId(EXCHANGE_ID);
        return exchange;
    }
}
