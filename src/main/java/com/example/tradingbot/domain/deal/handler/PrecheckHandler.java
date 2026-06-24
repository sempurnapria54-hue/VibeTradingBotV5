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
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.integration.service.IntegrationService;
import java.math.BigDecimal;
import java.util.List;
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
    private final DealActionPlanner planner;
    private final IntegrationService integrationService;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.PRECHECK;
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        if (isFalse(support.balanceUsable(dealContext))) {
            return DealTransition.command(support.refreshBalanceCommand(dealContext));
        }
        if (isTrue(foreignLiveRisk(dealContext))) {
            return support.markError(dealContext);
        }
        List<StrategyStep> entrySteps = support.stepsOfType(support.stepsFor(dealContext, Deal.Status.PRECHECK),
                StrategyStepType.ENTRY, StrategyStepType.GRID_ENTRY);
        if (isEmpty(entrySteps)) {
            return DealTransition.stay();
        }
        DealTransition inProgress = continueInProgressEntry(entrySteps, dealContext);
        if (nonNull(inProgress)) {
            return inProgress;
        }
        return evaluateEntry(entrySteps, dealContext);
    }

    /** Выходная проверка + продолжение уже начатого entry-действия (create→submit). */
    private DealTransition continueInProgressEntry(List<StrategyStep> entrySteps, DealContext dealContext) {
        for (StrategyStep step : entrySteps) {
            for (StrategyAction action : nullSafe(step)) {
                DealActionState state = support.findActionState(dealContext, action).orElse(null);
                if (isNull(state)) {
                    continue;
                }
                DealActionStateStatus status = state.getStatus();
                if (DealActionStateStatus.SUBMITTED.equals(status)) {
                    return DealTransition.transition(Deal.Status.ENTRY_SUBMITTED);
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

    /** Рабочая логика: первый entry-step с выполненным условием → запуск действия. */
    private DealTransition evaluateEntry(List<StrategyStep> entrySteps, DealContext dealContext) {
        ConditionEvaluationContext conditionContext = support.conditionContext(dealContext);
        for (StrategyStep step : entrySteps) {
            if (isTrue(support.conditionMet(step, conditionContext)) && isNotEmpty(step.getActions())) {
                StrategyAction action = step.getActions().getFirst();
                DealActionState state = support.findOrCreateActionState(dealContext, action);
                return support.reactToPlan(planner.plan(step, action, state, dealContext), dealContext);
            }
        }
        // Условие входа не выполнено, live risk ещё нет — закрыть кандидата без ошибки.
        return DealTransition.builder()
                .nextStatus(Deal.Status.CLOSED)
                .closeReason(Deal.CloseReason.ENTRY_CONDITION_EXPIRED)
                .build();
    }

    /** Чужой live risk на инструменте при отсутствии локальной позиции (Precheck-чистота). */
    private Boolean foreignLiveRisk(DealContext dealContext) {
        if (nonNull(dealContext.getDeal().getPosition())) {
            return false;
        }
        PositionExternalSnapshot snapshot = integrationService.getPosition(
                dealContext.getInstrument().getExternalId());
        return nonNull(snapshot) && hasLiveSize(snapshot.getExternalSize());
    }

    private boolean hasLiveSize(BigDecimal size) {
        return nonNull(size) && size.signum() > 0;
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
