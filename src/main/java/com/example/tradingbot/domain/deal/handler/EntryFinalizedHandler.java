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
import com.example.tradingbot.domain.deal.DealActionPlanner;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.FsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса ENTRY_FINALIZED: вход подтверждён, позиция открыта;
 * определяет, нужен ли реальный protection switch. Есть MAIN_PROTECTION
 * step → создать/подтвердить standalone main protection (algo) →
 * PROTECTION_SWITCHED; иначе attached SL входа покрывает риск → MANAGING.
 * Инвариант docs/rules/risk-creating-entry-protection.md: позицию с live
 * risk без подтверждённой защиты в MANAGING не уводим (нет ни main, ни
 * attached → ERROR). Позиция уже закрылась → recovery EXIT_PENDING. См.
 * docs/components/EntryFinalizedHandler.md.
 */
@Component
@RequiredArgsConstructor
public class EntryFinalizedHandler implements FsmHandler {

    private final DealFsmSupport support;
    private final DealActionPlanner planner;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ENTRY_FINALIZED;
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isFalse(support.positionLiveRisk(deal))) {
            return nonNull(deal.getPosition())
                    ? DealTransition.transition(Deal.Status.EXIT_PENDING)
                    : support.markError(dealContext);
        }
        List<StrategyStep> protectionSteps = support.stepsOfType(
                support.stepsFor(dealContext, Deal.Status.ENTRY_FINALIZED), StrategyStepType.MAIN_PROTECTION);
        if (isEmpty(protectionSteps)) {
            return toManagingIfProtected(dealContext);
        }
        DealTransition inProgress = continueProtection(protectionSteps, dealContext);
        if (nonNull(inProgress)) {
            return inProgress;
        }
        return startProtection(protectionSteps, dealContext);
    }

    private DealTransition continueProtection(List<StrategyStep> protectionSteps, DealContext dealContext) {
        for (StrategyStep step : protectionSteps) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, action).orElse(null);
                if (isNull(state)) {
                    continue;
                }
                DealActionStateStatus status = state.getStatus();
                if (DealActionStateStatus.SUBMITTED.equals(status)) {
                    return DealTransition.transition(Deal.Status.PROTECTION_SWITCHED);
                }
                if (DealActionStateStatus.FAILED.equals(status)) {
                    return support.markError(dealContext);
                }
                if (isActiveStage(status)) {
                    return support.reactToPlan(planner.plan(step, action, state, dealContext), dealContext);
                }
            }
        }
        return null;
    }

    private DealTransition startProtection(List<StrategyStep> protectionSteps, DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        for (StrategyStep step : protectionSteps) {
            if (isTrue(support.conditionMet(step, conditionContext)) && isNotEmpty(step.getActions())) {
                StrategyAction action = step.getActions().getFirst();
                DealActionState state = support.findOrCreateActionState(dealContext, action);
                return support.reactToPlan(planner.plan(step, action, state, dealContext), dealContext);
            }
        }
        // MAIN_PROTECTION есть, но условие не сработало — attached защита держит → MANAGING.
        return toManagingIfProtected(dealContext);
    }

    /** В MANAGING только если live risk покрыт защитой (attached SL входа); иначе ERROR + L3-холд. */
    private DealTransition toManagingIfProtected(DealContext dealContext) {
        Order entry = support.entryOrder(dealContext.getDeal());
        if (nonNull(entry) && isNotEmpty(entry.getAttachedAlgoOrders())) {
            return DealTransition.transition(Deal.Status.MANAGING);
        }
        // Live risk без резолвимой защиты = бесстоповая позиция постфактум → L3 (§8.C).
        return support.markErrorStopless(dealContext);
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
