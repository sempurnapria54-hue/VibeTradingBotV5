package com.example.tradingbot.domain.deal.action;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculationResult;
import com.example.tradingbot.domain.command.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.SubmitAlgoOrderCommandPayload;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per-pass executor CREATE-действия над standalone algo-order (SL/TP/OCO/
 * trailing). По стадии {@link DealActionState}: PLANNED → расчёт →
 * CREATE_ALGO_ORDER; CREATED → SUBMIT_ALGO_ORDER; SUBMITTED →
 * REFRESH_ALGO_ORDER. Risk-валидацию не проходит: algo-защита reduce-only,
 * позицию не открывает (docs/rules/risk-validator-scope.md). Направление —
 * закрывающее к направлению сделки. См. docs/decisions/fsm-execution-layering.md.
 */
@Component
@RequiredArgsConstructor
public class CreateAlgoOrderActionExecutor implements StrategyActionExecutor {

    private final StrategyActionCalculator calculator;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction
                && StrategyActionType.CREATE.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext) {
        return switch (state.getStatus()) {
            case PLANNED -> initialCommand((StrategyAlgoOrderAction) action, state, dealContext);
            case CREATED -> submitCommand(state, dealContext);
            case SUBMITTED -> refreshCommand(state, dealContext);
            default -> ActionPlan.empty();
        };
    }

    private ActionPlan initialCommand(StrategyAlgoOrderAction action, DealActionState state, DealContext dealContext) {
        StrategyActionCalculationResult calculation = calculator.calculate(action, dealContext);
        if (StrategyActionCalculationResult.Status.ERROR.equals(calculation.getStatus())) {
            return ActionPlan.calcError(calculation.getError());
        }
        return ActionPlan.command(createAlgoCommand(action, calculation.getCalculatedAction(), state, dealContext));
    }

    private ServiceCommand createAlgoCommand(StrategyAlgoOrderAction action, CalculatedStrategyAction calculated,
                                             DealActionState state, DealContext dealContext) {
        Condition condition = new Condition();
        condition.setType(action.getConditionType());
        CreateAlgoOrderCommandPayload payload = CreateAlgoOrderCommandPayload.builder()
                .conditionType(action.getConditionType())
                .direction(closeDirection(dealContext.getDeal().getDirection()))
                .instrumentExternalId(dealContext.getInstrument().getExternalId())
                .positionReducingOnly(Boolean.TRUE)
                .sizeContracts(calculated.getCalculatedSize().getSizeContracts())
                .condition(condition)
                .build();
        return command(ServiceCommandType.CREATE_ALGO_ORDER, dealContext, state, payload);
    }

    private ActionPlan submitCommand(DealActionState state, DealContext dealContext) {
        RuntimeTarget target = state.getTarget();
        if (isNull(target)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.SUBMIT_ALGO_ORDER, dealContext, state,
                new SubmitAlgoOrderCommandPayload(target.getEntityId())));
    }

    private ActionPlan refreshCommand(DealActionState state, DealContext dealContext) {
        RuntimeTarget target = state.getTarget();
        if (isNull(target)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.REFRESH_ALGO_ORDER, dealContext, state,
                new RefreshAlgoOrderCommandPayload(target.getEntityId())));
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

    private AlgoOrder.Direction closeDirection(StrategyTradeDirection dealDirection) {
        return StrategyTradeDirection.LONG.equals(dealDirection) ? AlgoOrder.Direction.SELL : AlgoOrder.Direction.BUY;
    }
}
