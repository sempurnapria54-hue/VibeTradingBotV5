package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingBoth;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingError;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingShutdown;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeWithCommandAndRung;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeWithCommands;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeWithEdges;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.TrancheEdge;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Активная сделка: реакция на каскад и её порядок — группа {@code U8}
 * документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealActiveHandler.md §«Рабочая логика» и §«Реакция на
 * устаревание данных»).
 *
 * <p><b>Базовая сборка.</b> Та же, что у {@code U7}; каскад подменён и
 * отдаёт объявленный входом кейса свод.
 *
 * <p><b>Кейс {@code U8.9} не прогоняется</b>: судьбу затребованной
 * каскадом ступени на тропе «шага нет» называет код, а на прочих тропах —
 * ни один носитель (находка {@code F-6}).
 */
class DealActiveCascadeReactionTest {

    private static final HoldSignal INSTRUMENT_RUNG =
            HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);

    private final DealActiveHarness harness = new DealActiveHarness();

    @Test
    @DisplayName("U8.1 — каскад молчит: проход идёт дальше, рёбра траншей — пустой перечень")
    void u8_1_aSilentCascadeLetsThePassGoOn() {
        DealTransition transition = harness.handle(context());

        assertThat(transition.getTrancheEdges()).isEmpty();
        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U8.2 — каскад просит ошибочную тропу: команда звена, рёбра траншей приложены")
    void u8_2_anErrorRequestEmitsTheErrorLinkAndCarriesTheEdges() {
        DealContext context = context();
        List<TrancheEdge> edges = List.of(edge(context));
        harness.givenCascade(cascadeAskingError(edges, INSTRUMENT_RUNG));
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_ERROR_COMMAND);

        DealTransition transition = harness.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getTrancheEdges()).isEqualTo(edges);
        assertThat(transition.getHoldSignal()).isEqualTo(INSTRUMENT_RUNG);
    }

    @Test
    @DisplayName("U8.3 — ошибочная тропа И ступень: просьбы не подменяют друг друга")
    void u8_3_anErrorRequestAndARungTravelTogether() {
        DealContext context = context();
        harness.givenCascade(cascadeAskingError(List.of(), INSTRUMENT_RUNG));
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_ERROR_COMMAND);

        DealTransition transition = harness.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
        assertThat(transition.getHoldSignal()).isEqualTo(INSTRUMENT_RUNG);
    }

    @Test
    @DisplayName("U8.4 — каскад просит управляемое сворачивание: причина закрытия RISK_CONTROL")
    void u8_4_aShutdownRequestCollapsesWithItsOwnReason() {
        harness.givenCascade(cascadeAskingShutdown(Deal.ShutdownReason.RISK_POLICY, null));

        DealTransition transition = harness.handle(context());

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getShutdownReason()).isEqualTo(Deal.ShutdownReason.RISK_POLICY);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
    }

    @Test
    @DisplayName("U8.5 — и ошибочная тропа, и сворачивание: побеждает ошибочная")
    void u8_5_theErrorRequestOutranksTheShutdownRequest() {
        harness.givenCascade(cascadeAskingBoth(Deal.ShutdownReason.RISK_POLICY));
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_ERROR_COMMAND);

        DealTransition transition = harness.handle(context());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U8.6 — две команды каскада: порядок прогона сохранён, агрегатный шаг не спрашивается")
    void u8_6_theCascadeCommandsTravelInRunOrder() {
        harness.givenCascade(cascadeWithCommands(ServiceCommandType.CREATE_ORDER_COMMAND,
                ServiceCommandType.SUBMIT_ORDER_COMMAND));

        DealTransition transition = harness.handle(context());

        assertThat(commandTypes(transition)).containsExactly(
                ServiceCommandType.CREATE_ORDER_COMMAND, ServiceCommandType.SUBMIT_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        harness.verifyDealStepNotSelected();
    }

    @Test
    @DisplayName("U8.7 — команд нет, одобрено одно ребро транша: проход считается занятым")
    void u8_7_anApprovedTrancheEdgeAloneCountsAsWork() {
        DealContext context = context();
        List<TrancheEdge> edges = List.of(edge(context));
        harness.givenCascade(cascadeWithEdges(edges));

        DealTransition transition = harness.handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getTrancheEdges()).isEqualTo(edges);
        harness.verifyDealStepNotSelected();
    }

    @Test
    @DisplayName("U8.8 — команда и ступень: едут вместе, ребра нет")
    void u8_8_aCommandAndARungTravelTogether() {
        harness.givenCascade(cascadeWithCommandAndRung(ServiceCommandType.CREATE_ORDER_COMMAND,
                INSTRUMENT_RUNG));

        DealTransition transition = harness.handle(context());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(transition.getHoldSignal()).isEqualTo(INSTRUMENT_RUNG);
        assertThat(transition.movesStatus()).isFalse();
    }

    // --- сборка ------------------------------------------------------------

    private TrancheEdge edge(DealContext context) {
        return new TrancheEdge(context.getDeal().getTranches().getFirst(),
                DealTranche.Status.EXIT_PENDING, null);
    }

    private DealContext context() {
        DealTranche managed = exposed(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "2");
        Deal active = deal(Deal.Status.ACTIVE, managed);
        active.getPositions().add(livePosition("2"));
        return contextBuilder(active).build();
    }

    private List<ServiceCommandType> commandTypes(DealTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
