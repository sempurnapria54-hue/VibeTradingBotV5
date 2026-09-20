package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.fsm.DealTrancheStateMachine;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheCascadeResult;
import com.example.tradingcore.domain.fsm.TrancheEdge;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Каскад траншей: свод исходов — группа {@code U16} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealActiveHandler.md §«Рабочая логика»; форма свода —
 * {@code TrancheCascadeResult}).
 *
 * <p><b>Базовая сборка.</b> Сделка с тремя траншами: два активных, один
 * терминальный; машина транша подменена и отдаёт объявленный входом
 * переход по каждому.
 *
 * <p><b>Аварийная просьба сильнее управляемой</b>, и порядок прогона на
 * этом не сказывается: обратный порядок отдал бы снятие риска
 * закрывающим действиям, которые считаются по данным, которым доверять
 * уже нельзя.
 */
class TrancheCascadeResultTest {

    private static final HoldSignal HARD =
            HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);

    private static final HoldSignal SOFT =
            HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);

    private static final Long TERMINAL_TRANCHE_ID = 23L;

    private final DealTrancheStateMachine machine = mock(DealTrancheStateMachine.class);

    private final TrancheCascade cascade = new TrancheCascade(machine);

    TrancheCascadeResultTest() {
        when(machine.run(any(), any())).thenReturn(TrancheTransition.stay());
    }

    @Test
    @DisplayName("U16.1 — оба активных транша молчат: свод пуст, признак работы ложен")
    void u16_1_aSilentCascadeProducesAnEmptyResult() {
        TrancheCascadeResult result = cascade.run(baseContext());

        assertThat(result.getCommands()).isEmpty();
        assertThat(result.getEdges()).isEmpty();
        assertThat(result.getDealErrorRequested()).isFalse();
        assertThat(result.getShutdownRequested()).isNull();
        assertThat(result.getHoldSignal()).isNull();
        assertThat(result.acted()).isFalse();
    }

    @Test
    @DisplayName("U16.2 — терминальный транш: прогона по нему нет")
    void u16_2_theTerminalTrancheIsNotRun() {
        DealContext context = baseContext();

        cascade.run(context);

        verify(machine, never()).run(any(), eq(terminalOf(context)));
    }

    @Test
    @DisplayName("U16.3 — два транша выдали команды: обе в своде, в порядке прогона")
    void u16_3_theCommandsKeepTheRunOrder() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.command(command(ServiceCommandType.CREATE_ORDER_COMMAND)));
        givenSecond(context, TrancheTransition.command(command(ServiceCommandType.SUBMIT_ORDER_COMMAND)));

        TrancheCascadeResult result = cascade.run(context);

        assertThat(result.getCommands().stream().map(ServiceCommand::getType).toList())
                .containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND,
                        ServiceCommandType.SUBMIT_ORDER_COMMAND);
    }

    @Test
    @DisplayName("U16.4 — первый транш двинул статус: одно ребро с его целью и его причиной")
    void u16_4_aMovedStatusBecomesAnEdgeInTheResult() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.close(DealTranche.CloseReason.TAKE_PROFIT));

        TrancheCascadeResult result = cascade.run(context);

        assertThat(result.getEdges()).singleElement().satisfies(edge -> {
            assertThat(edge.getTranche()).isEqualTo(firstOf(context));
            assertThat(edge.getStatus()).isEqualTo(DealTranche.Status.CLOSED);
            assertThat(edge.getCloseReason()).isEqualTo(DealTranche.CloseReason.TAKE_PROFIT);
        });
    }

    @Test
    @DisplayName("U16.5 — статус двинут без причины закрытия: подстановки не происходит")
    void u16_5_anEdgeWithoutAReasonCarriesAnEmptyReason() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING));

        TrancheCascadeResult result = cascade.run(context);

        assertThat(result.getEdges()).singleElement()
                .extracting(TrancheEdge::getCloseReason).isNull();
    }

    @Test
    @DisplayName("U16.6 — один транш просит ошибочную тропу: признак истинен независимо от того, который")
    void u16_6_theErrorRequestIsIndependentOfWhichTrancheAsked() {
        DealContext first = baseContext();
        givenFirst(first, TrancheTransition.escalate());

        DealContext second = baseContext();
        givenSecond(second, TrancheTransition.escalate());

        assertThat(cascade.run(first).getDealErrorRequested()).isTrue();
        assertThat(cascade.run(second).getDealErrorRequested()).isTrue();
    }

    @Test
    @DisplayName("U16.7 — оба назвали причину выхода: в своде ПЕРВАЯ по порядку прогона")
    void u16_7_theFirstShutdownReasonWins() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.requestShutdown(Deal.ShutdownReason.RISK_POLICY));
        givenSecond(context, TrancheTransition.requestShutdown(Deal.ShutdownReason.MARKET_DATA_EXPIRED));

        assertThat(cascade.run(context).getShutdownRequested()).isEqualTo(Deal.ShutdownReason.RISK_POLICY);
    }

    @Test
    @DisplayName("U16.8 — первый просит мягкую, второй жёсткую: в своде жёсткая")
    void u16_8_theHardRungWinsOverTheSoftOne() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.requestRung(SOFT));
        givenSecond(context, TrancheTransition.requestRung(HARD));

        assertThat(cascade.run(context).getHoldSignal()).isEqualTo(HARD);
    }

    @Test
    @DisplayName("U16.9 — первый просит жёсткую, второй мягкую: порядок прогона исход не меняет")
    void u16_9_theHardRungWinsRegardlessOfTheRunOrder() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.requestRung(HARD));
        givenSecond(context, TrancheTransition.requestRung(SOFT));

        assertThat(cascade.run(context).getHoldSignal()).isEqualTo(HARD);
    }

    @Test
    @DisplayName("U16.10 — ступень просит только второй транш: в своде она же")
    void u16_10_aRungAskedByTheSecondTrancheStillTravels() {
        DealContext context = baseContext();
        givenSecond(context, TrancheTransition.requestRung(SOFT));

        assertThat(cascade.run(context).getHoldSignal()).isEqualTo(SOFT);
    }

    @Test
    @DisplayName("U16.11 — живых траншей нет вовсе: машина транша не звалась ни разу")
    void u16_11_aDealWithoutLiveTranchesNeverCallsTheMachine() {
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE,
                tranche(TRANCHE_ID, DealTranche.Status.CLOSED))).build();

        TrancheCascadeResult result = cascade.run(context);

        assertThat(result.acted()).isFalse();
        verifyNoInteractions(machine);
    }

    @Test
    @DisplayName("U16.12 — транш выдал команду, ребра не двинул: признак работы истинен по команде")
    void u16_12_aCommandAloneMakesTheCascadeActed() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.command(command(ServiceCommandType.CREATE_ORDER_COMMAND)));

        assertThat(cascade.run(context).acted()).isTrue();
    }

    @Test
    @DisplayName("U16.13 — транш двинул ребро, команды не выдал: признак истинен по ребру")
    void u16_13_anEdgeAloneMakesTheCascadeActed() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING));

        assertThat(cascade.run(context).acted()).isTrue();
    }

    @Test
    @DisplayName("U16.14 — транш только попросил ступень: просьба работой не является")
    void u16_14_aRungRequestAloneIsNotWork() {
        DealContext context = baseContext();
        givenFirst(context, TrancheTransition.requestRung(HARD));

        TrancheCascadeResult result = cascade.run(context);

        assertThat(result.acted()).isFalse();
        assertThat(result.getHoldSignal()).isEqualTo(HARD);
    }

    // --- сборка ------------------------------------------------------------

    private void givenFirst(DealContext context, TrancheTransition transition) {
        when(machine.run(any(), eq(firstOf(context)))).thenReturn(transition);
    }

    private void givenSecond(DealContext context, TrancheTransition transition) {
        when(machine.run(any(), eq(secondOf(context)))).thenReturn(transition);
    }

    private DealTranche firstOf(DealContext context) {
        return context.getDeal().getTranches().get(0);
    }

    private DealTranche secondOf(DealContext context) {
        return context.getDeal().getTranches().get(1);
    }

    private DealTranche terminalOf(DealContext context) {
        return context.getDeal().getTranches().get(2);
    }

    /** Сделка с двумя активными траншами и одним терминальным. */
    private DealContext baseContext() {
        return contextBuilder(deal(Deal.Status.ACTIVE,
                tranche(TRANCHE_ID, DealTranche.Status.MANAGING),
                tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING),
                tranche(TERMINAL_TRANCHE_ID, DealTranche.Status.CLOSED))).build();
    }
}
