package com.example.tradingbot.domain.deal.action;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculationResult;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.command.risk.RiskBlockAction;
import com.example.tradingbot.domain.command.risk.RiskBlockResolver;
import com.example.tradingbot.domain.command.risk.RiskValidationResult;
import com.example.tradingbot.domain.command.risk.RiskValidator;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per-pass executor CREATE-действия над ordinary order. По стадии
 * {@link DealActionState}: PLANNED → расчёт → risk (для risk-creating, т.е.
 * не reduce-only) → CREATE_ORDER; CREATED → SUBMIT_ORDER; SUBMITTED →
 * REFRESH_ORDER. На продвинутых стадиях расчёт/риск не повторяются (нога по
 * фактам из target). Risk-block отдаётся {@link ActionPlan}'ом (реакцию
 * решает resolver в handler'е). См. docs/decisions/fsm-execution-layering.md.
 */
@Component
@RequiredArgsConstructor
public class CreateOrderActionExecutor implements StrategyActionExecutor {

    private final StrategyActionCalculator calculator;
    private final RiskValidator riskValidator;
    private final RiskBlockResolver riskBlockResolver;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyOrderAction
                && StrategyActionType.CREATE.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext) {
        return switch (state.getStatus()) {
            case PLANNED -> initialCommand(step, (StrategyOrderAction) action, state, dealContext);
            case CREATED -> submitCommand(state, dealContext);
            case SUBMITTED -> refreshCommand(state, dealContext);
            default -> ActionPlan.empty();
        };
    }

    private ActionPlan initialCommand(StrategyStep step, StrategyOrderAction action, DealActionState state,
                                      DealContext dealContext) {
        StrategyActionCalculationResult calculation = calculator.calculate(action, dealContext);
        if (StrategyActionCalculationResult.Status.ERROR.equals(calculation.getStatus())) {
            return ActionPlan.calcError(calculation.getError());
        }
        CalculatedStrategyAction calculated = calculation.getCalculatedAction();
        if (isRiskCreating(action)) {
            ActionPlan blocked = applyRisk(step, action, calculated, dealContext);
            if (nonNull(blocked)) {
                return blocked;
            }
        }
        return ActionPlan.command(createOrderCommand(action, calculated, state, dealContext));
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

    private ServiceCommand createOrderCommand(StrategyOrderAction action, CalculatedStrategyAction calculated,
                                              DealActionState state, DealContext dealContext) {
        String instId = dealContext.getInstrument().getExternalId();
        CreateOrderCommandPayload payload = CreateOrderCommandPayload.builder()
                .orderType(action.getOrderType())
                .strategyDirection(action.getDirection())
                .side(toSide(action.getDirection()))
                .instrumentExternalId(instId)
                .sizeContracts(calculated.getCalculatedSize().getSizeContracts())
                .price(calculated.getCalculatedPrice().getRoundedPrice())
                .sendPriceToExchange(calculated.getCalculatedPrice().getSendPriceToExchange())
                .positionReducingOnly(action.getPositionReducingOnly())
                .build();
        return command(ServiceCommandType.CREATE_ORDER, dealContext, state, payload);
    }

    private ActionPlan submitCommand(DealActionState state, DealContext dealContext) {
        RuntimeTarget target = state.getTarget();
        if (isNull(target)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.SUBMIT_ORDER, dealContext, state,
                new SubmitOrderCommandPayload(target.getEntityId())));
    }

    private ActionPlan refreshCommand(DealActionState state, DealContext dealContext) {
        RuntimeTarget target = state.getTarget();
        if (isNull(target)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.REFRESH_ORDER, dealContext, state,
                new RefreshOrderCommandPayload(target.getEntityId())));
    }

    private ServiceCommand command(ServiceCommandType type, DealContext dealContext, DealActionState state,
                                   ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(dealContext.getDeal().getId())
                .dealActionStateId(state.getId())
                .payload(payload)
                .build();
    }

    /** Risk-creating действие — открывающий/наращивающий (не reduce-only) ордер. */
    private Boolean isRiskCreating(StrategyOrderAction action) {
        return isNotTrue(action.getPositionReducingOnly());
    }

    private boolean isBlocking(RiskBlockAction.Type type) {
        return isFalse(RiskBlockAction.Type.CONTINUE.equals(type))
                && isFalse(RiskBlockAction.Type.CONTINUE_WITH_WARNING.equals(type));
    }

    private String toSide(StrategyTradeDirection direction) {
        return StrategyTradeDirection.LONG.equals(direction) ? Constants.Okx.SIDE_BUY : Constants.Okx.SIDE_SELL;
    }
}
