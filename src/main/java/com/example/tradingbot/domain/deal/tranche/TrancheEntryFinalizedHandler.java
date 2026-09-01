package com.example.tradingbot.domain.deal.tranche;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
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
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import java.util.List;
import java.util.Optional;
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
 * docs/components/TrancheEntryFinalizedHandler.md.
 */
@Component
@RequiredArgsConstructor
public class TrancheEntryFinalizedHandler implements TrancheFsmHandler {

    private final DealFsmSupport support;
    private final StrategyActionOrchestrator actionOrchestrator;

    @Override
    public DealTranche.Status supportedStatus() {
        return DealTranche.Status.ENTRY_FINALIZED;
    }

    @Override
    public Optional<TrancheTransition> checkEntry(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isFalse(support.positionLiveRisk(deal))) {
            // Нет живой позиции — защиту финализировать не над чем.
            return Optional.of(nonNull(deal.livePosition())
                    ? TrancheTransition.transition(DealTranche.Status.EXIT_PENDING)
                    : TrancheTransition.escalateToDealError());
        }
        return Optional.empty();
    }

    @Override
    public Optional<TrancheTransition> checkTransition(DealContext dealContext, DealTranche tranche) {
        for (StrategyStep step : protectionSteps(dealContext, tranche)) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, tranche, action).orElse(null);
                if (isNull(state)) {
                    continue;
                }
                if (DealActionStateStatus.SUBMITTED.equals(state.getStatus())) {
                    return Optional.of(TrancheTransition.transition(DealTranche.Status.PROTECTION_SWITCHED));
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
        List<StrategyStep> protectionSteps = protectionSteps(dealContext, tranche);
        if (isEmpty(protectionSteps)) {
            return toManagingIfProtected(dealContext, tranche);
        }
        TrancheTransition inProgress = continueProtection(protectionSteps, dealContext, tranche);
        if (nonNull(inProgress)) {
            return inProgress;
        }
        return startProtection(protectionSteps, dealContext, tranche);
    }

    private List<StrategyStep> protectionSteps(DealContext dealContext, DealTranche tranche) {
        return support.stepsOfType(
                support.stepsFor(dealContext, DealTranche.Status.ENTRY_FINALIZED), StrategyStepType.MAIN_PROTECTION);
    }

    /** Прогресс уже начатого (active) protection-действия; переходы (SUBMITTED/FAILED) отсеяны в checkTransition. */
    private TrancheTransition continueProtection(List<StrategyStep> protectionSteps, DealContext dealContext, DealTranche tranche) {
        for (StrategyStep step : protectionSteps) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, tranche, action).orElse(null);
                if (nonNull(state) && isActiveStage(state.getStatus())) {
                    return support.reactToTranchePlan(actionOrchestrator.plan(step, action, state, dealContext, tranche));
                }
            }
        }
        return null;
    }

    /**
     * Запуск ОЧЕРЕДНОГО действия пакета применимого шага защиты;
     * действие выбирает оркестратор (пакет целиком, за проход — одно).
     */
    private TrancheTransition startProtection(List<StrategyStep> protectionSteps, DealContext dealContext, DealTranche tranche) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        boolean anyEligible = false;
        for (StrategyStep step : protectionSteps) {
            if (isFalse(support.stepEligible(step, dealContext, tranche, conditionContext))) {
                continue;
            }
            anyEligible = true;
            StrategyAction action = actionOrchestrator.nextAction(step, dealContext, tranche).orElse(null);
            if (nonNull(action)) {
                DealActionState state = support.findOrCreateActionState(dealContext, tranche, action);
                return support.reactToTranchePlan(actionOrchestrator.plan(step, action, state, dealContext, tranche));
            }
        }
        if (anyEligible) {
            // Шаг защиты допустим, а начинать нечего — ждём следующего прохода:
            // переход по ветви «условие не сработало» подписал бы ожидание
            // чужой причиной.
            return TrancheTransition.stay();
        }
        // MAIN_PROTECTION есть, но условие не сработало — attached защита держит → MANAGING.
        return toManagingIfProtected(dealContext, tranche);
    }

    /** В MANAGING только если live risk покрыт защитой (attached SL входа); иначе ERROR + L3-холд. */
    private TrancheTransition toManagingIfProtected(DealContext dealContext, DealTranche tranche) {
        Order entry = support.entryOrder(dealContext.getDeal());
        if (nonNull(entry) && isTrue(entry.hasActiveAttachedProtection())) {
            return TrancheTransition.transition(DealTranche.Status.MANAGING);
        }
        // Live risk без резолвимой защиты = бесстоповая позиция постфактум → L3 (§8.C).
        return TrancheTransition.escalateToDealError();
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
