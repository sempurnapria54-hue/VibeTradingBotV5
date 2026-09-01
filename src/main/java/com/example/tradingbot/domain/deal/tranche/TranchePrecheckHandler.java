package com.example.tradingbot.domain.deal.tranche;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.TrancheTransition;
import com.example.tradingbot.domain.deal.TrancheFsmHandler;
import com.example.tradingbot.domain.deal.action.StrategyActionOrchestrator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса PRECHECK: готовит сделку к созданию entry order.
 * Входные проверки (баланс, чистота инструмента), рабочая логика
 * (ENTRY/GRID_ENTRY step → condition → calc → risk → CREATE_ORDER →
 * SUBMIT_ORDER, set-leverage перед постановкой внутри submit-executor),
 * выходная проверка (entry submitted → ENTRY_SUBMITTED). Condition false и
 * нет live risk → закрыть кандидата (CLOSED + ENTRY_CONDITION_EXPIRED);
 * risk-block без live risk → CLOSED + RISK_CONTROL (через resolver). См.
 * docs/components/TranchePrecheckHandler.md.
 */
@Component
@RequiredArgsConstructor
public class TranchePrecheckHandler implements TrancheFsmHandler {

    private final DealFsmSupport support;
    private final StrategyActionOrchestrator actionOrchestrator;

    @Override
    public DealTranche.Status supportedStatus() {
        return DealTranche.Status.PRECHECK;
    }

    @Override
    public Optional<TrancheTransition> checkEntry(DealContext dealContext, DealTranche tranche) {
        if (isFalse(support.balanceUsable(dealContext))) {
            return Optional.of(TrancheTransition.command(support.refreshBalanceCommand(dealContext)));
        }
        if (isTrue(support.foreignLiveRisk(dealContext))) {
            return Optional.of(TrancheTransition.escalateToDealError());
        }
        if (isEmpty(entrySteps(dealContext, tranche))) {
            // Нет entry-шагов на этом статусе — делать нечего.
            return Optional.of(TrancheTransition.stay());
        }
        return Optional.empty();
    }

    @Override
    public Optional<TrancheTransition> checkTransition(DealContext dealContext, DealTranche tranche) {
        for (StrategyStep step : entrySteps(dealContext, tranche)) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, tranche, action).orElse(null);
                if (isNull(state)) {
                    continue;
                }
                if (DealActionStateStatus.SUBMITTED.equals(state.getStatus())) {
                    return Optional.of(TrancheTransition.transition(DealTranche.Status.ENTRY_SUBMITTED));
                }
                if (DealActionStateStatus.FAILED.equals(state.getStatus())) {
                    return Optional.of(TrancheTransition.escalateToDealError());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        List<StrategyStep> entrySteps = entrySteps(dealContext, tranche);
        TrancheTransition inProgress = continueInProgressEntry(entrySteps, dealContext, tranche);
        if (nonNull(inProgress)) {
            return inProgress;
        }
        return evaluateEntry(entrySteps, dealContext, tranche);
    }

    private List<StrategyStep> entrySteps(DealContext dealContext, DealTranche tranche) {
        return support.stepsOfType(support.stepsFor(dealContext, DealTranche.Status.PRECHECK),
                StrategyStepType.ENTRY, StrategyStepType.GRID_ENTRY);
    }

    /** Прогресс уже начатого (active) entry-действия; переходы (SUBMITTED/FAILED) отсеяны в checkTransition. */
    private TrancheTransition continueInProgressEntry(List<StrategyStep> entrySteps, DealContext dealContext, DealTranche tranche) {
        for (StrategyStep step : entrySteps) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, tranche, action).orElse(null);
                if (nonNull(state) && isActiveStage(state.getStatus())) {
                    return support.reactToTranchePlan(actionOrchestrator.plan(step, action, state, dealContext, tranche));
                }
            }
        }
        return null;
    }

    /** Рабочая логика: первый entry-step с выполненным условием → запуск действия. */
    private TrancheTransition evaluateEntry(List<StrategyStep> entrySteps, DealContext dealContext, DealTranche tranche) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        for (StrategyStep step : entrySteps) {
            if (isTrue(support.conditionMet(step, conditionContext)) && isNotEmpty(step.getActions())) {
                StrategyAction action = step.getActions().getFirst();
                DealActionState state = support.findOrCreateActionState(dealContext, tranche, action);
                return support.reactToTranchePlan(actionOrchestrator.plan(step, action, state, dealContext, tranche));
            }
        }
        // Условие входа не выполнено, live risk ещё нет — закрыть кандидата без ошибки.
        return TrancheTransition.builder()
                .nextStatus(DealTranche.Status.CLOSED)
                .closeReason(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED)
                .build();
    }

    private boolean isActiveStage(DealActionStateStatus status) {
        return DealActionStateStatus.PLANNED.equals(status)
                || DealActionStateStatus.CREATED.equals(status)
                || DealActionStateStatus.RETRY_PENDING.equals(status);
    }

    private List<StrategyAction> nullSafe(StrategyStep step) {
        return isEmpty(step.getActions()) ? List.of() : step.getActions();
    }
}
