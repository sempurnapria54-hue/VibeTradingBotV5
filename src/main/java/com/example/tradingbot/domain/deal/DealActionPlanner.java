package com.example.tradingbot.domain.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandFactory;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculationResult;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.command.risk.RiskBlockAction;
import com.example.tradingbot.domain.command.risk.RiskBlockResolver;
import com.example.tradingbot.domain.command.risk.RiskValidationResult;
import com.example.tradingbot.domain.command.risk.RiskValidator;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Прогоняет один выбранный StrategyAction через конвейер расчёт → риск →
 * фабрика и возвращает {@link ActionPlan} (команда / risk-block / ошибка
 * расчёта). На продвинутых стадиях DealActionState (CREATED/SUBMITTED)
 * фабрика выдаёт следующую ногу по фактам без повторного расчёта/риска. Для
 * risk-creating действий (открывающий не-reduce-only ордер) вызывает
 * RiskValidator → RiskBlockResolver (docs/rules/risk-validator-scope.md);
 * reduce-only/cleanup/finalization риск-валидацию не проходят. Сам команды
 * не исполняет и статус сделки не двигает — это делает handler/оркестратор.
 */
@Component
@RequiredArgsConstructor
public class DealActionPlanner {

    private final StrategyActionCalculator calculator;
    private final RiskValidator riskValidator;
    private final RiskBlockResolver riskBlockResolver;
    private final ServiceCommandFactory commandFactory;
    private final DealActionStateDataService dealActionStateDataService;

    public ActionPlan plan(StrategyStep step, StrategyAction action, DealActionState actionState,
                           DealContext dealContext) {
        if (isRetryPending(actionState)) {
            if (isFalse(retryDue(actionState))) {
                return ActionPlan.empty();
            }
            rearmForRetry(actionState);
        }
        if (isAdvancedStage(actionState)) {
            return ActionPlan.command(commandFactory.nextCommand(null, actionState, dealContext).orElse(null));
        }
        StrategyActionCalculationResult calculation = calculator.calculate(action, dealContext);
        if (StrategyActionCalculationResult.Status.ERROR.equals(calculation.getStatus())) {
            return ActionPlan.calcError(calculation.getError());
        }
        CalculatedStrategyAction calculated = calculation.getCalculatedAction();
        if (isTrue(requiresRiskValidation(action))) {
            ActionPlan blocked = applyRisk(step, action, calculated, dealContext);
            if (nonNull(blocked)) {
                return blocked;
            }
        }
        Optional<ServiceCommand> command = commandFactory.nextCommand(calculated, actionState, dealContext);
        return command.map(ActionPlan::command).orElseGet(ActionPlan::empty);
    }

    /** null = риск разрешил продолжить; иначе — блокирующая реакция risk-layer. */
    private ActionPlan applyRisk(StrategyStep step, StrategyAction action, CalculatedStrategyAction calculated,
                                 DealContext dealContext) {
        RiskValidationResult risk = riskValidator.validate(calculated, dealContext);
        if (RiskValidationResult.RiskDecision.ALLOWED.equals(risk.getDecision())) {
            return null;
        }
        RiskBlockAction blockAction = riskBlockResolver.resolve(dealContext, dealContext.getDeal().getStatus(),
                step, action, calculated, risk);
        if (isBlocking(blockAction.getType())) {
            return ActionPlan.blocked(blockAction);
        }
        return null;
    }

    private boolean isAdvancedStage(DealActionState actionState) {
        if (isNull(actionState)) {
            return false;
        }
        return DealActionStateStatus.CREATED.equals(actionState.getStatus())
                || DealActionStateStatus.SUBMITTED.equals(actionState.getStatus());
    }

    private boolean isRetryPending(DealActionState actionState) {
        return nonNull(actionState) && DealActionStateStatus.RETRY_PENDING.equals(actionState.getStatus());
    }

    /** Повтор разрешён, если nextRetryAt не задан или уже наступил (иначе ждём backoff). */
    private Boolean retryDue(DealActionState actionState) {
        return isNull(actionState.getNextRetryAt())
                || isFalse(OffsetDateTime.now(ZoneOffset.UTC).isBefore(actionState.getNextRetryAt()));
    }

    /**
     * Перевод RETRY_PENDING обратно на стадию, с которой команда
     * пере-эмитится: target отсутствует → PLANNED (повтор CREATE); target
     * создан → CREATED (повтор SUBMIT; D-B3 recovery-by-clientId делает
     * повторный submit идемпотентным). REFRESH-провал поднимается через
     * CREATED → SUBMIT (recovery находит сущность) → SUBMITTED → REFRESH.
     */
    private void rearmForRetry(DealActionState actionState) {
        RuntimeTarget target = actionState.getTarget();
        actionState.setStatus(isNull(target)
                ? DealActionStateStatus.PLANNED
                : DealActionStateStatus.CREATED);
        dealActionStateDataService.save(actionState);
    }

    /** Risk-creating действие — открывающий/наращивающий (не reduce-only) ордер. */
    private Boolean requiresRiskValidation(StrategyAction action) {
        if (action instanceof StrategyOrderAction orderAction) {
            return isNotTrue(orderAction.getPositionReducingOnly());
        }
        return false;
    }

    private boolean isBlocking(RiskBlockAction.Type type) {
        return isFalse(RiskBlockAction.Type.CONTINUE.equals(type))
                && isFalse(RiskBlockAction.Type.CONTINUE_WITH_WARNING.equals(type));
    }
}
