package com.example.tradingbot.domain.deal.handler;

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
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.FsmHandler;
import com.example.tradingbot.domain.deal.action.StrategyActionOrchestrator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса MANAGING: сопровождает открытую позицию по стратегии
 * (PROTECTION_ADJUSTMENT / PARTIAL_EXIT / GRID_MANAGEMENT / EXIT /
 * FAIL_SAFE). Позиция закрылась/закрывается → EXIT_PENDING. Иначе:
 * продолжить начатое действие либо запустить первое применимое (condition →
 * calc → risk → команды). EXIT → CLOSE_POSITION → EXIT_PENDING. Ремодел
 * защиты (PROTECTION_ADJUSTMENT, REPLACE) секвенсит петля по фактам —
 * leg-оркестрация — forward-refinement (фабрика REPLACE-ног пока не
 * порождает). См. docs/components/ManagingHandler.md.
 */
@Component
@RequiredArgsConstructor
public class ManagingHandler implements FsmHandler {

    private final DealFsmSupport support;
    private final StrategyActionOrchestrator actionOrchestrator;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.MANAGING;
    }

    @Override
    public Optional<DealTransition> checkEntry(DealContext dealContext) {
        if (isFalse(support.positionLiveRisk(dealContext.getDeal()))) {
            // Нет живой позиции — сопровождать нечего; дочистка в EXIT_PENDING.
            return Optional.of(DealTransition.transition(Deal.Status.EXIT_PENDING));
        }
        return Optional.empty();
    }

    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        for (StrategyStep step : managingSteps(dealContext)) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, action).orElse(null);
                if (isNull(state)) {
                    continue;
                }
                if (DealActionStateStatus.FAILED.equals(state.getStatus())) {
                    return Optional.of(support.markError(dealContext));
                }
                if (isExitSubmitted(step, state.getStatus())) {
                    return Optional.of(DealTransition.transition(Deal.Status.EXIT_PENDING));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        List<StrategyStep> steps = managingSteps(dealContext);
        DealTransition inProgress = continueInProgress(steps, dealContext);
        if (nonNull(inProgress)) {
            return inProgress;
        }
        return startApplicable(steps, dealContext);
    }

    private List<StrategyStep> managingSteps(DealContext dealContext) {
        return support.stepsOfType(support.stepsFor(dealContext, Deal.Status.MANAGING),
                StrategyStepType.PROTECTION_ADJUSTMENT, StrategyStepType.PARTIAL_EXIT,
                StrategyStepType.GRID_MANAGEMENT, StrategyStepType.EXIT);
    }

    /** Прогресс уже начатого (active) действия; переходы (FAILED/exit) отсеяны в checkTransition. */
    private DealTransition continueInProgress(List<StrategyStep> steps, DealContext dealContext) {
        for (StrategyStep step : steps) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, action).orElse(null);
                if (nonNull(state) && isActiveStage(state.getStatus())) {
                    return support.reactToPlan(actionOrchestrator.plan(step, action, state, dealContext), dealContext);
                }
            }
        }
        return null;
    }

    private DealTransition startApplicable(List<StrategyStep> steps, DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        for (StrategyStep step : steps) {
            if (isTrue(support.conditionMet(step, conditionContext)) && isNotEmpty(step.getActions())) {
                StrategyAction action = step.getActions().getFirst();
                DealActionState state = support.findOrCreateActionState(dealContext, action);
                return support.reactToPlan(actionOrchestrator.plan(step, action, state, dealContext), dealContext);
            }
        }
        return DealTransition.stay();
    }

    private boolean isExitSubmitted(StrategyStep step, DealActionStateStatus status) {
        return StrategyStepType.EXIT.equals(step.getStepType())
                && DealActionStateStatus.SUBMITTED.equals(status);
    }

    private boolean isActiveStage(DealActionStateStatus status) {
        return DealActionStateStatus.PLANNED.equals(status)
                || DealActionStateStatus.CREATED.equals(status)
                || DealActionStateStatus.SUBMITTED.equals(status)
                || DealActionStateStatus.RETRY_PENDING.equals(status);
    }

    private List<StrategyAction> nullSafe(StrategyStep step) {
        return isEmpty(step.getActions()) ? List.of() : step.getActions();
    }
}
