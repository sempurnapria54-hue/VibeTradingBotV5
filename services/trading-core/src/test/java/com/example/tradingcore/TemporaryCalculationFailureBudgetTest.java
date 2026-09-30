package com.example.tradingcore;

import static com.example.strategy.engine.calc.util.CalculationErrorCodes.FEE_RATE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculationError;
import com.example.strategy.engine.calc.CalculationErrorType;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.StrategyActionOrchestrator;
import com.example.tradingcore.domain.fsm.StrategyWorkRunner;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Временная ошибка расчёта на строке исполнения: пока бюджет повтора есть,
 * она ждёт отката; исчерпав его, едет дальше постоянной — и сделка уходит
 * ошибочной тропой, а не теряет шаг молча
 * (docs/components/models/CalculationError.md).
 *
 * <p>Клетки {@code U22.20}, {@code U22.21} документа
 * {@code .claude/tests/cases/trading-core-fsm.md}. Строка исполнения — настоящая
 * модель; подменены коллабораторы: оркестратор, политика повтора и сборка
 * контекста, отдающая строку.
 */
class TemporaryCalculationFailureBudgetTest {

    private static final Long ACTION_ID = 7L;

    private final StrategyActionOrchestrator orchestrator = mock(StrategyActionOrchestrator.class);
    private final RetryPolicyService retryPolicyService = mock(RetryPolicyService.class);
    private final StrategyWorkRunner runner = new StrategyWorkRunner(
            orchestrator, retryPolicyService, mock(DealActionStateDataService.class));

    private final StrategyStep step = new StrategyStep();
    private final DealTranche tranche = new DealTranche();
    private final DealContext dealContext = mock(DealContext.class);
    private final DealActionState state = new DealActionState();

    @Test
    @DisplayName("U22.20 — временная ошибка при живом бюджете: строка ждёт отката, сделка в аварию не уходит")
    void temporaryFailureWithBudgetLeftWaitsForRetry() {
        ActionPlan plan = startWith(CalculationError.temporary(FEE_RATE_UNAVAILABLE, "taker fee rate is not resolved"), true);

        assertThat(state.getStatus()).isEqualTo(DealActionStateStatus.RETRY_PENDING);
        assertThat(plan.getCalculationError().getType()).isEqualTo(CalculationErrorType.TEMPORARY);
        assertThat(runner.calculationFailureIsFatal(plan.getCalculationError())).isFalse();
    }

    @Test
    @DisplayName("U22.21 — временная ошибка с исчерпанным бюджетом: строка отказала, сделка уходит ошибочной тропой")
    void temporaryFailureWithExhaustedBudgetIsFatal() {
        ActionPlan plan = startWith(CalculationError.temporary(FEE_RATE_UNAVAILABLE, "taker fee rate is not resolved"), false);

        assertThat(state.getStatus()).isEqualTo(DealActionStateStatus.FAILED);
        assertThat(plan.getCalculationError().getCode()).isEqualTo(FEE_RATE_UNAVAILABLE);
        assertThat(plan.getCalculationError().getType()).isEqualTo(CalculationErrorType.PERMANENT);
        assertThat(runner.calculationFailureIsFatal(plan.getCalculationError())).isTrue();
    }

    private ActionPlan startWith(CalculationError error, Boolean budgetLeft) {
        StrategyAction action = mock(StrategyAction.class);
        when(action.getId()).thenReturn(ACTION_ID);
        when(orchestrator.nextAction(step, dealContext, tranche)).thenReturn(Optional.of(action));
        when(orchestrator.plan(step, action, null, dealContext, tranche))
                .thenReturn(ActionPlan.calculationFailed(error));
        when(dealContext.actionState(ACTION_ID, tranche)).thenReturn(Optional.of(state));
        when(retryPolicyService.canRetry(any(DealActionState.class), isNull())).thenReturn(budgetLeft);
        return runner.startNext(step, dealContext, tranche);
    }
}
