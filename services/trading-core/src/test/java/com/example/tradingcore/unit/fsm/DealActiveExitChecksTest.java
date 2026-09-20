package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingRung;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.step;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.StepSelection;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Активная сделка: агрегатный шаг, удаление определения и выходная
 * проверка — группа {@code U9} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealActiveHandler.md §«Выходные проверки»).
 *
 * <p><b>Базовая сборка.</b> Та же, что у {@code U7}; каскад молчит; отбор
 * шага подменён и отдаёт объявленный входом исход.
 *
 * <p><b>Кейс {@code U9.13} не прогоняется</b> по той же причине, что
 * {@code U8.9}: потерю затребованной ступени на тропах агрегатного шага и
 * удалённого определения не называет ни один дом (находка {@code F-6}).
 */
class DealActiveExitChecksTest {

    private final DealActiveHarness harness = new DealActiveHarness();

    @Test
    @DisplayName("U9.1 — агрегатный шаг типа EXIT: причина закрытия STRATEGY_EXIT без причины выхода")
    void u9_1_anExitStepCollapsesWithTheStrategyExitReason() {
        harness.givenDealStep(StepSelection.of(step(1L, StrategyStepType.EXIT)));

        DealTransition transition = harness.handle(managedContext());

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.STRATEGY_EXIT);
        assertThat(transition.getShutdownReason()).isNull();
    }

    @Test
    @DisplayName("U9.2 — агрегатный шаг типа FAIL_SAFE: то же ребро, причина RISK_CONTROL")
    void u9_2_aFailSafeStepCollapsesWithTheRiskControlReason() {
        harness.givenDealStep(StepSelection.of(step(1L, StrategyStepType.FAIL_SAFE)));

        DealTransition transition = harness.handle(managedContext());

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
    }

    @Test
    @DisplayName("U9.3 — реакция «управляемое сворачивание»: причина выхода — устаревание данных")
    void u9_3_aGracefulCloseEscalationCollapsesWithTheExpiredDataReason() {
        harness.givenDealStep(StepSelection.escalated(MarketDataExpiredAction.GRACEFUL_CLOSE));

        DealTransition transition = harness.handle(managedContext());

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getShutdownReason()).isEqualTo(Deal.ShutdownReason.MARKET_DATA_EXPIRED);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
    }

    @Test
    @DisplayName("U9.4 — реакция «аварийное снятие риска»: ребра нет, только просьба жёсткой ступени")
    void u9_4_aKillSwitchEscalationOnlyRequestsTheInstrumentRung() {
        harness.givenDealStep(StepSelection.escalated(MarketDataExpiredAction.KILL_SWITCH));

        DealTransition transition = harness.handle(managedContext());

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal().getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(transition.getHoldSignal().getRung()).isEqualTo(HoldRung.HARD);
        assertThat(transition.getHoldSignal().getCode())
                .isEqualTo(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
    }

    @Test
    @DisplayName("U9.5 — определение удалено: причина выхода STRATEGY_DELETED, закрытия — STRATEGY_EXIT")
    void u9_5_aDeletedDefinitionCollapsesTheDeal() {
        DealContext context = contextBuilder(managedDeal())
                .strategy(deletedDefinition())
                .build();

        DealTransition transition = harness.handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getShutdownReason()).isEqualTo(Deal.ShutdownReason.STRATEGY_DELETED);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.STRATEGY_EXIT);
    }

    @Test
    @DisplayName("U9.6 — удаление И аварийная реакция: проверка удаления стои́т после реакции")
    void u9_6_theExpiredDataReactionOutranksTheDeletedDefinition() {
        harness.givenDealStep(StepSelection.escalated(MarketDataExpiredAction.KILL_SWITCH));
        DealContext context = contextBuilder(managedDeal())
                .strategy(deletedDefinition())
                .build();

        DealTransition transition = harness.handle(context);

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal().getCode())
                .isEqualTo(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
    }

    @Test
    @DisplayName("U9.7 — определение живо, шага нет, транши не терминальны: ступень каскада донесена")
    void u9_7_aQuietPassStillCarriesTheCascadeRung() {
        HoldSignal rung = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
        harness.givenCascade(cascadeAskingRung(rung));
        DealContext context = contextBuilder(managedDeal()).strategy(liveDefinition()).build();

        DealTransition transition = harness.handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getTrancheEdges()).isEmpty();
        assertThat(transition.getHoldSignal()).isEqualTo(rung);
    }

    @Test
    @DisplayName("U9.8 — все транши терминальны при состоявшемся входе: причина по старшинству")
    void u9_8_allTranchesClosedWithOperationsGoesToTheCoordinatedExit() {
        DealTranche closed = fills(tranche(TRANCHE_ID, DealTranche.Status.CLOSED), "5", "5");
        closed.setCloseReason(DealTranche.CloseReason.TAKE_PROFIT);

        DealTransition transition = harness.handle(contextBuilder(deal(Deal.Status.ACTIVE, closed)).build());

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.TAKE_PROFIT);
    }

    @Test
    @DisplayName("U9.9 — все транши терминальны, входа не было: команда звена и причина на модели")
    void u9_9_allTranchesClosedWithoutOperationsFinalizesFromHere() {
        DealContext context = closedWithoutEntry(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_EXIT_ACTION,
                ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);

        DealTransition transition = harness.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(context.getDeal().getCloseReason())
                .isEqualTo(Deal.CloseReason.ENTRY_CONDITION_EXPIRED);
    }

    @Test
    @DisplayName("U9.10 — причина закрытия на сделке уже стои́т: прежнее значение не переписывается")
    void u9_10_anExistingCloseReasonIsNotOverwritten() {
        DealContext context = closedWithoutEntry(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        context.getDeal().setCloseReason(Deal.CloseReason.EXTERNAL_CLOSE);
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_EXIT_ACTION,
                ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);

        harness.handle(context);

        assertThat(context.getDeal().getCloseReason()).isEqualTo(Deal.CloseReason.EXTERNAL_CLOSE);
    }

    @Test
    @DisplayName("U9.11 — причина по старшинству не выводится: команда эмитится, подстановки нет")
    void u9_11_anUnresolvedSeniorityLeavesTheReasonEmpty() {
        DealContext context = closedWithoutEntry(null);
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_EXIT_ACTION,
                ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);

        DealTransition transition = harness.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);
        assertThat(context.getDeal().getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U9.12 — у звена уже живая строка: переход пустой")
    void u9_12_aLiveExecutionRowLeavesThePassEmpty() {
        DealContext context = closedWithoutEntry(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);

        DealTransition transition = harness.handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    // --- сборка ------------------------------------------------------------

    /** Сделка, все транши которой терминальны, а входа не было. */
    private DealContext closedWithoutEntry(DealTranche.CloseReason reason) {
        DealTranche closed = tranche(TRANCHE_ID, DealTranche.Status.CLOSED);
        closed.setCloseReason(reason);
        return contextBuilder(deal(Deal.Status.ACTIVE, closed)).build();
    }

    private Strategy deletedDefinition() {
        return definition(Strategy.Status.DELETED);
    }

    /** Живое определение: сворачивания оно не запускает. */
    private Strategy liveDefinition() {
        return definition(Strategy.Status.ACTIVE);
    }

    private Strategy definition(Strategy.Status status) {
        Strategy definition = new Strategy();
        definition.setInternalId("st-0001");
        definition.setStatus(status);
        return definition;
    }

    private DealContext managedContext() {
        return contextBuilder(managedDeal()).build();
    }

    private Deal managedDeal() {
        DealTranche managed = exposed(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "2");
        Deal active = deal(Deal.Status.ACTIVE, managed);
        active.getPositions().add(livePosition("2"));
        return active;
    }

    private List<ServiceCommandType> commandTypes(DealTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
