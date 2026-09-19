package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static com.example.tradingcore.unit.safety.SafetyFixture.accountContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldRungEdgeService;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Маршрутизация сигнала: мягкая тропа против полной — группа `U3`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/HoldService.md §Назначение).
 *
 * <p><b>Базовая сборка:</b> сервис блокировки; координатор, ребро
 * подъёма и сервис отчёта подменены; контекст объекта несёт счёт и
 * инструмент.
 */
class HoldRoutingTest {

    private final AnomalyReportService reports = mock(AnomalyReportService.class);
    private final SafetyHoldCoordinator coordinator = mock(SafetyHoldCoordinator.class);
    private final HoldRungEdgeService edge = mock(HoldRungEdgeService.class);

    private HoldService holdService;

    @BeforeEach
    void setUp() {
        holdService = new HoldService(reports, coordinator, edge);
        when(edge.raise(any(), any())).thenReturn(true);
    }

    /** Жёсткий сигнал целиком отдаётся координатору полной реакции. */
    @Test
    @DisplayName("U3.1 — жёсткий сигнал: позван координатор, ребро напрямую не позвано, журнала этот сервис не пишет")
    void u3_1_aHardSignalGoesToTheCoordinator() {
        HoldSignal signal = HoldSignal.instrument(CODE);
        DealContext context = pairContext();

        holdService.raise(signal, context);

        verify(coordinator).react(signal, context);
        verify(edge, never()).raise(any(), any());
        verifyNoInteractions(reports);
    }

    /** Мягкий инструментный сигнал ведёт сам сервис: строка плюс ребро. */
    @Test
    @DisplayName("U3.2 — мягкий сигнал инструмента: позваны писатель состояния и ребро, координатор не позван")
    void u3_2_aSoftInstrumentSignalStaysInTheService() {
        HoldSignal signal = HoldSignal.instrumentSoft(CODE);
        DealContext context = pairContext();

        holdService.raise(signal, context);

        verify(reports).journalState(context, signal, null);
        verify(edge).raise(signal, context);
        verifyNoInteractions(coordinator);
    }

    /** Мягкая ступень исполняется на обоих радиусах; составы разводят лестницы. */
    @Test
    @DisplayName("U3.3 — мягкий сигнал счёта: та же тропа, что у инструментного")
    void u3_3_aSoftAccountSignalTakesTheSamePath() {
        HoldSignal signal = HoldSignal.exchangeAccountSoft(CODE);
        DealContext context = accountContext();

        holdService.raise(signal, context);

        verify(reports).journalState(context, signal, null);
        verify(edge).raise(signal, context);
        verifyNoInteractions(coordinator);
    }

    /**
     * Запись идёт до гарда перехода: гард отвечает на «переставился ли
     * статус», а строка — на «почему контур встал».
     */
    @Test
    @DisplayName("U3.4 — мягкий сигнал: запись журнала раньше ребра подъёма")
    void u3_4_theJournalRowPrecedesTheRungEdge() {
        HoldSignal signal = HoldSignal.instrumentSoft(CODE);
        DealContext context = pairContext();

        holdService.raise(signal, context);

        InOrder order = inOrder(reports, edge);
        order.verify(reports).journalState(context, signal, null);
        order.verify(edge).raise(signal, context);
    }

    /** Журнал реакцию не гейтит: отказ записи логируется и ход продолжается. */
    @Test
    @DisplayName("U3.5 — писатель состояния бросает: ребро позвано всё равно, исключения наружу нет, в логе запись")
    void u3_5_aFailingJournalDoesNotGateTheReaction() {
        HoldSignal signal = HoldSignal.instrumentSoft(CODE);
        DealContext context = pairContext();
        when(reports.journalState(any(), any(), any())).thenThrow(new IllegalStateException("db is down"));

        try (SafetyLogCapture log = SafetyLogCapture.attach(HoldService.class)) {
            assertThatCode(() -> holdService.raise(signal, context))
                    .as("U3.5: отказ журнала реакцию не гейтит")
                    .doesNotThrowAnyException();

            assertThat(log.messages())
                    .as("отказ журнала оставляет свою запись")
                    .anyMatch(message -> message.contains("Journal of a soft safety signal failed"));
        }
        verify(edge).raise(signal, context);
    }

    /**
     * Факт подъёма клаузы «журнал не гейтит» не наследует: отказ ребра
     * уходит вызывающему, а строка, записанная до него, остаётся.
     */
    @Test
    @DisplayName("U3.6 — ребро подъёма бросает: исключение уходит вызывающему, строка журнала остаётся")
    void u3_6_aFailingRungEdgeFailsTheCall() {
        HoldSignal signal = HoldSignal.instrumentSoft(CODE);
        DealContext context = pairContext();
        doThrow(new IllegalStateException("outbox is down")).when(edge).raise(any(), any());

        assertThatThrownBy(() -> holdService.raise(signal, context))
                .as("U3.6: состояние «строка есть, ступени нет, факта нет» достижимо")
                .isInstanceOf(IllegalStateException.class);

        verify(reports).journalState(context, signal, null);
    }

    /** Охрана второго рубежа: пустой сигнал до тропы не доезжает. */
    @Test
    @DisplayName("U3.7 — сигнала нет вовсе: холостой ход, не позван никто")
    void u3_7_anAbsentSignalIsANoOp() {
        holdService.raise(null, pairContext());

        verifyNoInteractions(coordinator);
        verify(edge, never()).raise(any(), any());
        verifyNoInteractions(reports);
    }

    /**
     * Идемпотентность реакции и идемпотентность отчёта — разные ключи:
     * строка пишется и там, где гард перехода гасит ступень.
     */
    @Test
    @DisplayName("U3.8 — мягкий сигнал на стоящей ступени: строка пишется, ребро отвечает «не переставилась»")
    void u3_8_theJournalRowIsWrittenOverAStandingRung() {
        HoldSignal signal = HoldSignal.instrumentSoft(CODE);
        DealContext context = pairContext();
        when(edge.raise(any(), any())).thenReturn(false);

        holdService.raise(signal, context);

        verify(reports).journalState(context, signal, null);
        verify(edge).raise(signal, context);
        verify(coordinator, never()).react(any(), any());
    }
}
