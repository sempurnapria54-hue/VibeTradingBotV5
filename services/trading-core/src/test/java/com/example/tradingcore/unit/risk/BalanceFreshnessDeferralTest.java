package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.BALANCE_FRESHNESS;
import static com.example.tradingcore.unit.risk.RiskFixture.balanceSnapshot;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entrySourceAction;
import static com.example.tradingcore.unit.risk.RiskFixture.instrument;
import static com.example.tradingcore.unit.risk.RiskFixture.minutesAgo;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionSourceAction;
import static com.example.tradingcore.unit.risk.RiskFixture.reducingOnlyAction;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.CalculationError;
import com.example.strategy.engine.calc.StrategyActionCalculationResult;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.calc.CalculationContextFactory;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.ActionRiskGate;
import com.example.tradingcore.domain.command.strategy.CreateAlgoOrderActionExecutor;
import com.example.tradingcore.domain.command.strategy.CreateOrderActionExecutor;
import com.example.tradingcore.domain.command.strategy.ExitRoundingReader;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отсрочка акта, создающего риск, до свежего снимка средств — группа
 * {@code U32} документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §«Проверки средств счёта»; носитель
 * заказа добычи — docs/components/models/ActionPlan.md).
 *
 * <p><b>Базовая сборка.</b> Исполнители создания собраны настоящими; расчёт,
 * сборка контекста расчёта и узел преконтроля подменены, чтобы было видно,
 * дошёл ли акт до них. Толерантность свежести — 5 минут; свежий снимок
 * снят минуту назад, несвежий — десять. Строка исполнения запланирована.
 *
 * <p><b>Предикат свежести — настоящий</b> (контекст прохода и модель снимка):
 * его пересборка в тесте проверяла бы исполнителя против предиката, которого
 * в проде нет.
 */
class BalanceFreshnessDeferralTest {

    private static final Long STATE_ID = 71L;

    private final CalculationContextFactory contextFactory = mock(CalculationContextFactory.class);
    private final StrategyActionCalculator calculator = mock(StrategyActionCalculator.class);
    private final ActionRiskGate riskGate = mock(ActionRiskGate.class);
    private final ExitRoundingReader exitRoundingReader = new ExitRoundingReader(
            mock(AnomalyReportService.class), mock(DealActionStateDataService.class));
    private final DealContextProperties properties = new DealContextProperties();

    private final CreateOrderActionExecutor orderExecutor = new CreateOrderActionExecutor(contextFactory,
            calculator, riskGate, exitRoundingReader, properties);
    private final CreateAlgoOrderActionExecutor algoExecutor = new CreateAlgoOrderActionExecutor(contextFactory,
            calculator, riskGate, exitRoundingReader);

    BalanceFreshnessDeferralTest() {
        properties.setBalanceFreshness(BALANCE_FRESHNESS);
        when(contextFactory.build(any(), any(), any()))
                .thenReturn(CalculationContext.builder().instrument(instrument("USDT")).build());
        when(riskGate.gate(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("U32.1 — акт, создающий риск, снимок старше толерантности: план отсрочки, до расчёта не доходит")
    void u32_1_aStaleSnapshotDefersTheRiskCreatingAct() {
        DealContext pass = pass(stale());

        ActionPlan plan = orderExecutor.next(new StrategyStep(), entrySourceAction(), planned(STATE_ID), pass,
                tranche(List.of(), List.of()));

        assertThat(plan.getAwaitingBalance()).isTrue();
        assertThat(plan.hasCommand()).isFalse();
        assertThat(plan.isBlocked()).isFalse();
        assertThat(plan.hasCalculationError()).isFalse();
        verify(contextFactory, never()).build(any(), any(), any());
        verify(calculator, never()).calculate(any());
        verify(riskGate, never()).gate(any(), any(), any());
    }

    /** Отложенный акт решённым не считается: ноги у него нет, соседу откладываться не за чем. */
    @Test
    @DisplayName("U32.2 — отложенный акт решённым входом прохода не отмечается")
    void u32_2_aDeferredActIsNotMarkedDecided() {
        DealContext pass = pass(stale());

        orderExecutor.next(new StrategyStep(), entrySourceAction(), planned(STATE_ID), pass,
                tranche(List.of(), List.of()));

        assertThat(pass.riskCreatingEntryDecidedBesides(planned(STATE_ID + 1))).isFalse();
    }

    @Test
    @DisplayName("U32.3 — снимка средств нет вовсе: план отсрочки")
    void u32_3_noSnapshotDefersTheAct() {
        ActionPlan plan = orderExecutor.next(new StrategyStep(), entrySourceAction(), planned(STATE_ID),
                pass(null), tranche(List.of(), List.of()));

        assertThat(plan.getAwaitingBalance()).isTrue();
        verify(calculator, never()).calculate(any());
    }

    /** Свежесть, чей срок не объявлен, не измерена — снимок читается несвежим. */
    @Test
    @DisplayName("U32.4 — толерантность не объявлена, снимок снят минуту назад: план отсрочки")
    void u32_4_anUndeclaredToleranceDefersTheAct() {
        properties.setBalanceFreshness(null);

        ActionPlan plan = orderExecutor.next(new StrategyStep(), entrySourceAction(), planned(STATE_ID),
                pass(fresh()), tranche(List.of(), List.of()));

        assertThat(plan.getAwaitingBalance()).isTrue();
        verify(calculator, never()).calculate(any());
    }

    @Test
    @DisplayName("U32.5 — акт, создающий риск, свежий снимок: расчёт, преконтроль, команда заведения ноги")
    void u32_5_aFreshSnapshotLetsTheActThrough() {
        when(calculator.calculate(any())).thenReturn(StrategyActionCalculationResult.success(entryAction()));

        ActionPlan plan = orderExecutor.next(new StrategyStep(), entrySourceAction(), planned(STATE_ID),
                pass(fresh()), tranche(List.of(), List.of()));

        assertThat(plan.getCommand().getType()).isEqualTo(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(plan.getAwaitingBalance()).isFalse();
        verify(riskGate).gate(any(), any(), any());
    }

    /** Reduce-only нога риск снимает: отсрочка ради проверки контура ослабляла бы выход. */
    @Test
    @DisplayName("U32.6 — reduce-only нога, снимок старше толерантности: команда без отсрочки")
    void u32_6_aReduceOnlyLegIsNotDeferred() {
        when(calculator.calculate(any())).thenReturn(StrategyActionCalculationResult.success(reducingOnlyAction()));
        StrategyOrderAction reducing = entrySourceAction();
        reducing.setPositionReducingOnly(true);

        ActionPlan plan = orderExecutor.next(new StrategyStep(), reducing, planned(STATE_ID), pass(stale()),
                tranche(List.of(), List.of()));

        assertThat(plan.getCommand().getType()).isEqualTo(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(plan.getAwaitingBalance()).isFalse();
        verify(riskGate, never()).gate(any(), any(), any());
    }

    /** Защита свежести не ждёт: её отсрочка ослабляла бы то, что защищает. */
    @Test
    @DisplayName("U32.7 — защитное создание, снимок старше толерантности: команда без отсрочки")
    void u32_7_aProtectiveCreationIsNotDeferred() {
        when(calculator.calculate(any())).thenReturn(
                StrategyActionCalculationResult.success(protectionAction("2910")));

        ActionPlan plan = algoExecutor.next(new StrategyStep(), protectionSourceAction(), planned(STATE_ID),
                pass(stale()), tranche(List.of(), List.of()));

        assertThat(plan.getCommand().getType()).isEqualTo(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND);
        assertThat(plan.getAwaitingBalance()).isFalse();
    }

    /** Отсрочка стои́т до расчёта в ПЛАНИРОВАНИИ; заведённая нога ведётся по фактам. */
    @Test
    @DisplayName("U32.8 — заведённая нога, снимок старше толерантности: команда отправки без отсрочки")
    void u32_8_aCreatedLegIsSubmittedWithoutDeferral() {
        DealActionState created = planned(STATE_ID);
        created.setStatus(DealActionStateStatus.CREATED);
        created.targetAt(TargetEntityType.ORDER, 900L);

        ActionPlan plan = orderExecutor.next(new StrategyStep(), entrySourceAction(), created, pass(stale()),
                tranche(List.of(), List.of()));

        assertThat(plan.getCommand().getType()).isEqualTo(ServiceCommandType.SUBMIT_ORDER_COMMAND);
        assertThat(plan.getAwaitingBalance()).isFalse();
    }

    /**
     * Пять исходов плана взаимоисключающи: у каждой фабрики истинен ровно
     * один признак из пяти.
     */
    @Test
    @DisplayName("U32.9 — исходы плана: ровно один из пяти у каждой фабрики")
    void u32_9_exactlyOneOfTheFiveOutcomes() {
        ActionPlan command = ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CREATE_ORDER_COMMAND).build());
        ActionPlan blocked = ActionPlan.blocked(RiskBlockAction.builder()
                .type(RiskBlockAction.Type.SKIP_ACTION).build());
        ActionPlan failed = ActionPlan.calculationFailed(CalculationError.temporary("PROBE", "probe"));
        ActionPlan awaiting = ActionPlan.awaitingBalance();
        ActionPlan nothing = ActionPlan.nothing();

        assertThat(outcomes(command)).containsExactly(true, false, false, false, false);
        assertThat(outcomes(blocked)).containsExactly(false, true, false, false, false);
        assertThat(outcomes(failed)).containsExactly(false, false, true, false, false);
        assertThat(outcomes(awaiting)).containsExactly(false, false, false, true, false);
        assertThat(outcomes(nothing)).containsExactly(false, false, false, false, true);
    }

    // --- сборка ------------------------------------------------------------

    /** Признаки исходов в порядке: команда, реакция, ошибка расчёта, отсрочка, пусто. */
    private List<Boolean> outcomes(ActionPlan plan) {
        return List.of(plan.hasCommand(), plan.isBlocked(), plan.hasCalculationError(),
                plan.getAwaitingBalance(), plan.isEmpty());
    }

    private DealContext pass(BalanceContainer snapshot) {
        return contextBuilder(emptyDeal()).balanceContainer(snapshot).build();
    }

    private BalanceContainer fresh() {
        return balanceSnapshot(minutesAgo(1), "1000", "1000", "1000");
    }

    private BalanceContainer stale() {
        return balanceSnapshot(minutesAgo(10), "1000", "1000", "1000");
    }

    private DealActionState planned(Long id) {
        DealActionState state = new DealActionState();
        state.setId(id);
        state.setDealId(1L);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }
}
