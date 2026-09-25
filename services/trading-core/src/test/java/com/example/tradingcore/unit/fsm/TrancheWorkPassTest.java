package com.example.tradingcore.unit.fsm;

import static com.example.strategy.engine.calc.util.CalculationErrorCodes.FEE_RATE_UNAVAILABLE;
import static com.example.strategy.engine.calc.util.CalculationErrorCodes.PROTECTION_LADDER_STEP_BELOW_MIN_SIZE;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.step;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculationError;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.StrategyActionOrchestrator;
import com.example.tradingcore.domain.fsm.StepSelection;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.domain.fsm.StrategyWorkRunner;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.util.Constants;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Рабочий блок прохода и диспозиция плана действия — группа {@code U22}
 * документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealTrancheStateMachine.md §«Конструкция обработчика —
 * та же, что у сделки»; карта реакций —
 * docs/components/RiskBlockResolver.md).
 *
 * <p><b>Базовая сборка.</b> Блок собран настоящей диспозицией; отбор шага
 * подменён; исполнитель звеньев подменён.
 *
 * <p><b>Исполнитель работы стратегии — НАБЛЮДАТЕЛЬ поверх настоящего:</b>
 * подменены только два его прохода ({@code advanceLive},
 * {@code startNext}), а карв-аут отказа расчёта остаётся настоящим — иначе
 * клетки {@code U22.12}-{@code U22.14} мерили бы подменённый ответ, а не
 * перечень кодов дома.
 */
class TrancheWorkPassTest {

    private final StrategyStepSelector stepSelector = mock(StrategyStepSelector.class);

    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);

    private final StrategyWorkRunner workRunner = spy(new StrategyWorkRunner(
            mock(StrategyActionOrchestrator.class), mock(RetryPolicyService.class),
            mock(DealActionStateDataService.class)));

    private final TrancheActionDisposition disposition =
            new TrancheActionDisposition(systemActionExecutor, workRunner);

    private final TrancheWorkPass workPass = new TrancheWorkPass(stepSelector, workRunner, disposition);

    TrancheWorkPassTest() {
        doReturn(Optional.empty()).when(workRunner).advanceLive(any(), any());
        doReturn(ActionPlan.nothing()).when(workRunner).startNext(any(), any(), any());
        when(stepSelector.selectTrancheStep(any(), any())).thenReturn(StepSelection.none());
        when(systemActionExecutor.next(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("U22.1 — живое исполнение есть: отбор шага не спрашивается ни разу")
    void u22_1_aLiveExecutionIsAdvancedWithoutTheStepSelector() {
        doReturn(Optional.of(ActionPlan.of(command(ServiceCommandType.SUBMIT_ORDER_COMMAND))))
                .when(workRunner).advanceLive(any(), any());

        TrancheTransition transition = run();

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.SUBMIT_ORDER_COMMAND);
        verify(stepSelector, never()).selectTrancheStep(any(), any());
    }

    @Test
    @DisplayName("U22.2 — живого исполнения нет, отбор дал шаг: блок начинает его действие")
    void u22_2_aSelectedStepStartsItsNextAction() {
        StrategyStep selected = step(1L, StrategyStepType.MAIN_PROTECTION);
        when(stepSelector.selectTrancheStep(any(), any())).thenReturn(StepSelection.of(selected));
        doReturn(ActionPlan.of(command(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND)))
                .when(workRunner).startNext(eq(selected), any(), any());

        TrancheTransition transition = run();

        assertThat(commandTypes(transition))
                .containsExactly(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND);
        verify(workRunner).startNext(eq(selected), any(), any());
    }

    @Test
    @DisplayName("U22.3 — живого исполнения нет, отбор молчит: переход пустой")
    void u22_3_aSilentSelectorLeavesThePassEmpty() {
        assertEmpty(run());
    }

    @Test
    @DisplayName("U22.4 — реакция «аварийное снятие риска»: жёсткая ступень инструмента")
    void u22_4_aKillSwitchEscalationRequestsTheInstrumentRung() {
        when(stepSelector.selectTrancheStep(any(), any()))
                .thenReturn(StepSelection.escalated(MarketDataExpiredAction.KILL_SWITCH));

        TrancheTransition transition = run();

        HoldSignal rung = transition.getHoldSignal();
        assertThat(rung.getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(rung.getRung()).isEqualTo(HoldRung.HARD);
        assertThat(rung.getCode()).isEqualTo(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
        assertThat(transition.getShutdownRequested()).isNull();
    }

    @Test
    @DisplayName("U22.5 — реакция «управляемое сворачивание»: просьба выхода сделки, ступени нет")
    void u22_5_aGracefulCloseEscalationRequestsTheDealShutdown() {
        when(stepSelector.selectTrancheStep(any(), any()))
                .thenReturn(StepSelection.escalated(MarketDataExpiredAction.GRACEFUL_CLOSE));

        TrancheTransition transition = run();

        assertThat(transition.getShutdownRequested()).isEqualTo(Deal.ShutdownReason.MARKET_DATA_EXPIRED);
        assertThat(transition.getHoldSignal()).isNull();
    }

    @Test
    @DisplayName("U22.6 — план несёт команду: ребра и просьб нет")
    void u22_6_aPlanWithACommandCarriesOnlyIt() {
        TrancheTransition transition = dispose(
                ActionPlan.of(command(ServiceCommandType.CREATE_ORDER_COMMAND)));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
    }

    @Test
    @DisplayName("U22.7 — реакция «закрыть кандидата»: терминал транша с причиной RISK_CONTROL")
    void u22_7_theCloseCandidateReactionClosesTheTranche() {
        TrancheTransition transition = dispose(blocked(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.RISK_CONTROL);
    }

    @Test
    @DisplayName("U22.8 — реакция «увести сделку в ошибку»: просьба ошибочной тропы")
    void u22_8_theMoveDealToErrorReactionEscalates() {
        assertThat(dispose(blocked(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR)).getDealErrorRequested())
                .isTrue();
    }

    @Test
    @DisplayName("U22.9 — реакция «затребовать обновление»: команда добычи снимка средств через звено")
    void u22_9_theRequestRefreshReactionFetchesTheBalanceSnapshot() {
        when(systemActionExecutor.next(eq(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION), any(), isNull(),
                eq(ServiceCommandType.REFRESH_BALANCE_COMMAND), any()))
                .thenReturn(Optional.of(command(ServiceCommandType.REFRESH_BALANCE_COMMAND)));

        TrancheTransition transition = dispose(blocked(RiskBlockAction.Type.REQUEST_REFRESH));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_BALANCE_COMMAND);
    }

    @Test
    @DisplayName("U22.10 — реакция «пропустить действие»: переход пустой")
    void u22_10_theSkipActionReactionLeavesThePassEmpty() {
        assertEmpty(dispose(blocked(RiskBlockAction.Type.SKIP_ACTION)));
    }

    @Test
    @DisplayName("U22.11 — разрешающая реакция: её исход — молчание, а не команда")
    void u22_11_theAllowingReactionsAreSilent() {
        assertEmpty(dispose(blocked(RiskBlockAction.Type.CONTINUE)));
        assertEmpty(dispose(blocked(RiskBlockAction.Type.CONTINUE_WITH_WARNING)));
    }

    @Test
    @DisplayName("U22.12 — постоянная ошибка расчёта вне карв-аута: просьба ошибочной тропы")
    void u22_12_aPermanentCalculationErrorOutsideTheCarveOutEscalates() {
        TrancheTransition transition = dispose(ActionPlan.calculationFailed(
                CalculationError.permanent(FEE_RATE_UNAVAILABLE, "fee rate is not observed")));

        assertThat(transition.getDealErrorRequested()).isTrue();
    }

    @Test
    @DisplayName("U22.13 — постоянная ошибка из карв-аута: строка отказала, сделка в аварию не уходит")
    void u22_13_aPermanentCalculationErrorFromTheCarveOutStaysQuiet() {
        assertEmpty(dispose(ActionPlan.calculationFailed(CalculationError.permanent(
                PROTECTION_LADDER_STEP_BELOW_MIN_SIZE, "ladder step is below the minimum size"))));
    }

    @Test
    @DisplayName("U22.14 — временная ошибка расчёта: переход пустой")
    void u22_14_aTemporaryCalculationErrorStaysQuiet() {
        assertEmpty(dispose(ActionPlan.calculationFailed(
                CalculationError.temporary(FEE_RATE_UNAVAILABLE, "fee rate is not observed yet"))));
    }

    @Test
    @DisplayName("U22.15 — план пуст: переход пустой")
    void u22_15_anEmptyPlanLeavesThePassEmpty() {
        assertEmpty(dispose(ActionPlan.nothing()));
    }

    @Test
    @DisplayName("U22.16 — признак «блок что-то сказал» истинен на каждом из пяти по отдельности")
    void u22_16_theSpokePredicateIsTrueOnEachOfTheFiveOutcomes() {
        HoldSignal rung = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);

        assertThat(workPass.spoke(TrancheTransition.command(
                command(ServiceCommandType.CREATE_ORDER_COMMAND)))).isTrue();
        assertThat(workPass.spoke(TrancheTransition.escalate())).isTrue();
        assertThat(workPass.spoke(TrancheTransition.moveTo(DealTranche.Status.EXIT_PENDING))).isTrue();
        assertThat(workPass.spoke(TrancheTransition.requestShutdown(
                Deal.ShutdownReason.MARKET_DATA_EXPIRED))).isTrue();
        assertThat(workPass.spoke(TrancheTransition.requestRung(rung))).isTrue();
    }

    @Test
    @DisplayName("U22.17 — тот же признак на пустом переходе: ложен")
    void u22_17_theSpokePredicateIsFalseOnAnEmptyTransition() {
        assertThat(workPass.spoke(TrancheTransition.stay())).isFalse();
    }

    @Test
    @DisplayName("U22.18 — «управляемое сворачивание» у сворачивающейся сделки: просьба исполнена, блок молчит")
    void u22_18_aGracefulCloseEscalationOnACollapsingDealIsSilent() {
        when(stepSelector.selectTrancheStep(any(), any()))
                .thenReturn(StepSelection.escalated(MarketDataExpiredAction.GRACEFUL_CLOSE));

        TrancheTransition transition = runOnCollapsingDeal();

        assertEmpty(transition);
        assertThat(workPass.spoke(transition)).isFalse();
    }

    @Test
    @DisplayName("U22.19 — «аварийное снятие риска» у сворачивающейся сделки: жёсткая ступень остаётся")
    void u22_19_aKillSwitchEscalationOnACollapsingDealStillRequestsTheRung() {
        when(stepSelector.selectTrancheStep(any(), any()))
                .thenReturn(StepSelection.escalated(MarketDataExpiredAction.KILL_SWITCH));

        TrancheTransition transition = runOnCollapsingDeal();

        HoldSignal rung = transition.getHoldSignal();
        assertThat(rung.getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(rung.getRung()).isEqualTo(HoldRung.HARD);
        assertThat(rung.getCode()).isEqualTo(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
        assertThat(transition.getShutdownRequested()).isNull();
    }

    // --- сборка ------------------------------------------------------------

    private TrancheTransition run() {
        DealContext context = context();
        return workPass.run(context, context.getDeal().getTranches().getFirst());
    }

    /** Сделка в координированном выходе, транш на подтверждённом входе. */
    private TrancheTransition runOnCollapsingDeal() {
        DealContext context = contextBuilder(deal(Deal.Status.EXIT_PENDING,
                tranche(TRANCHE_ID, DealTranche.Status.ENTRY_FINALIZED))).build();
        return workPass.run(context, context.getDeal().getTranches().getFirst());
    }

    private TrancheTransition dispose(ActionPlan plan) {
        DealContext context = context();
        return disposition.dispose(plan, context, context.getDeal().getTranches().getFirst());
    }

    private ActionPlan blocked(RiskBlockAction.Type type) {
        return ActionPlan.blocked(RiskBlockAction.builder().type(type).comment("probe").build());
    }

    private void assertEmpty(TrancheTransition transition) {
        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
        assertThat(transition.getShutdownRequested()).isNull();
        assertThat(transition.getHoldSignal()).isNull();
    }

    private DealContext context() {
        return contextBuilder(deal(Deal.Status.ACTIVE,
                tranche(TRANCHE_ID, DealTranche.Status.MANAGING))).build();
    }

    private List<ServiceCommandType> commandTypes(TrancheTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
