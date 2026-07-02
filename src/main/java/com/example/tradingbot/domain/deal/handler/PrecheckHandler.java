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
 * FSM handler статуса PRECHECK: готовит сделку к созданию entry order.
 * Входные проверки (баланс, чистота инструмента), рабочая логика
 * (ENTRY/GRID_ENTRY step → condition → calc → risk → CREATE_ORDER →
 * SUBMIT_ORDER, set-leverage перед постановкой внутри submit-executor),
 * выходная проверка (entry submitted → ENTRY_SUBMITTED). Condition false и
 * нет live risk → закрыть кандидата (CLOSED + ENTRY_CONDITION_EXPIRED);
 * risk-block без live risk → CLOSED + RISK_CONTROL (через resolver). См.
 * docs/components/PrecheckHandler.md.
 */
@Component
@RequiredArgsConstructor
public class PrecheckHandler implements FsmHandler {

    private final DealFsmSupport support;
    private final StrategyActionOrchestrator actionOrchestrator;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PRECHECK;
    }

    @Override
    public Optional<DealTransition> checkEntry(DealContext dealContext) {
        if (isFalse(support.balanceUsable(dealContext))) {
            return Optional.of(DealTransition.command(support.refreshBalanceCommand(dealContext)));
        }
        if (isTrue(support.foreignLiveRisk(dealContext))) {
            return Optional.of(support.markError(dealContext));
        }
        if (isEmpty(entrySteps(dealContext))) {
            // Нет entry-шагов на этом статусе — делать нечего.
            return Optional.of(DealTransition.stay());
        }
        return Optional.empty();
    }

    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        for (StrategyStep step : entrySteps(dealContext)) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, action).orElse(null);
                if (isNull(state)) {
                    continue;
                }
                if (DealActionStateStatus.SUBMITTED.equals(state.getStatus())) {
                    return Optional.of(DealTransition.transition(Deal.Status.ENTRY_SUBMITTED));
                }
                if (DealActionStateStatus.FAILED.equals(state.getStatus())) {
                    return Optional.of(support.markError(dealContext));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        List<StrategyStep> entrySteps = entrySteps(dealContext);
        DealTransition inProgress = continueInProgressEntry(entrySteps, dealContext);
        if (nonNull(inProgress)) {
            return inProgress;
        }
        return evaluateEntry(entrySteps, dealContext);
    }

    private List<StrategyStep> entrySteps(DealContext dealContext) {
        return support.stepsOfType(support.stepsFor(dealContext, Deal.Status.PRECHECK),
                StrategyStepType.ENTRY, StrategyStepType.GRID_ENTRY);
    }

    /** Прогресс уже начатого (active) entry-действия; переходы (SUBMITTED/FAILED) отсеяны в checkTransition. */
    private DealTransition continueInProgressEntry(List<StrategyStep> entrySteps, DealContext dealContext) {
        for (StrategyStep step : entrySteps) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, action).orElse(null);
                if (nonNull(state) && isActiveStage(state.getStatus())) {
                    return support.reactToPlan(actionOrchestrator.plan(step, action, state, dealContext), dealContext);
                }
            }
        }
        return null;
    }

    /** Рабочая логика: первый entry-step с выполненным условием → запуск действия. */
    private DealTransition evaluateEntry(List<StrategyStep> entrySteps, DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        for (StrategyStep step : entrySteps) {
            if (isTrue(support.conditionMet(step, conditionContext)) && isNotEmpty(step.getActions())) {
                StrategyAction action = step.getActions().getFirst();
                DealActionState state = support.findOrCreateActionState(dealContext, action);
                return support.reactToPlan(actionOrchestrator.plan(step, action, state, dealContext), dealContext);
            }
        }
        // Условие входа не выполнено, live risk ещё нет — закрыть кандидата без ошибки.
        return DealTransition.builder()
                .nextStatus(Deal.Status.CLOSED)
                .closeReason(Deal.CloseReason.ENTRY_CONDITION_EXPIRED)
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
