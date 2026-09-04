package com.example.tradingbot.domain.deal.action;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.calc.CalculatedPrice;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.ResolvedStopLossPrice;
import com.example.tradingbot.domain.command.calc.ResolvedTakeProfitPrice;
import com.example.tradingbot.domain.command.calc.ResolvedTrailingPrice;
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
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per-pass executor CREATE-действия над standalone algo-order (SL/TP/OCO/
 * trailing). По стадии {@link DealActionState}: PLANNED → расчёт →
 * CREATE_ALGO_ORDER; CREATED → SUBMIT_ALGO_ORDER; SUBMITTED →
 * REFRESH_ALGO_ORDER. **Прогоняет преконтроль по ветке ослабления защиты**
 * (docs/rules/risk-validator-scope.md: создание защитной заявки — любой,
 * включая покрывающую экспозицию полностью): действие reduce-only и
 * позицию не открывает, поэтому оба слагаемых акта нулевые, но состав
 * неравенств классом действия не сужается. Направление — закрывающее к
 * направлению сделки. См. docs/processes/fsm-execution-layering.md.
 */
@Component
@RequiredArgsConstructor
public class CreateAlgoOrderActionExecutor implements StrategyActionExecutor {

    private final StrategyActionCalculator calculator;
    private final ActionRiskGate riskGate;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction
                && StrategyActionType.CREATE_ACTION.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext,
                           DealTranche tranche) {
        return switch (state.getStatus()) {
            case PLANNED -> initialCommand((StrategyAlgoOrderAction) action, state, dealContext, tranche);
            case CREATED -> submitCommand(state, dealContext);
            case SUBMITTED -> refreshCommand(state, dealContext);
            default -> ActionPlan.empty();
        };
    }

    private ActionPlan initialCommand(StrategyAlgoOrderAction action, DealActionState state, DealContext dealContext,
                                      DealTranche tranche) {
        StrategyActionCalculationResult calculation = calculator.calculate(action, dealContext);
        if (StrategyActionCalculationResult.Status.ERROR.equals(calculation.getStatus())) {
            return ActionPlan.calcError(calculation.getError());
        }
        CalculatedStrategyAction calculated = calculation.getCalculatedAction();
        ActionPlan blocked = riskGate.blockingPlan(calculated, dealContext, tranche);
        if (nonNull(blocked)) {
            return blocked;
        }
        return ActionPlan.command(createAlgoCommand(action, calculated, state, dealContext, tranche));
    }

    private ServiceCommand createAlgoCommand(StrategyAlgoOrderAction action, CalculatedStrategyAction calculated,
                                             DealActionState state, DealContext dealContext, DealTranche tranche) {
        CreateAlgoOrderCommandPayload payload = CreateAlgoOrderCommandPayload.builder()
                .dealTrancheId(tranche.getId())
                .conditionType(action.getConditionType())
                .direction(closeDirection(dealContext.getDeal().getDirection()))
                .instrumentExternalId(dealContext.getInstrument().getExternalId())
                .positionReducingOnly(Boolean.TRUE)
                .sizeContracts(calculated.getCalculatedSize().getSizeContracts())
                .condition(buildCondition(action, calculated.getCalculatedPrice()))
                .build();
        return command(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND, dealContext, state, payload);
    }

    /**
     * Готовое дерево condition с рассчитанными trigger/trailing ценами
     * (контракт {@link CreateAlgoOrderCommandPayload}). Тип условия задаёт,
     * какие ноги заполняются из {@link CalculatedPrice}; иначе защитный
     * ордер ушёл бы на биржу без триггерной цены.
     */
    private Condition buildCondition(StrategyAlgoOrderAction action, CalculatedPrice price) {
        AlgoOrder.ConditionType conditionType = action.getConditionType();
        Condition condition = new Condition();
        condition.setType(conditionType);
        switch (conditionType) {
            case STOP_LOSS, PARTIAL_STOP_LOSS -> condition.setTrigger(new Trigger(stopLossLeg(price), null));
            case TAKE_PROFIT, PARTIAL_TAKE_PROFIT -> condition.setTrigger(new Trigger(null, takeProfitLeg(price)));
            case OCO_FULL -> condition.setTrigger(new Trigger(stopLossLeg(price), takeProfitLeg(price)));
            case TRAILING_PERCENTS, TRAILING_VALUE -> condition.setTrailing(trailingLeg(price));
        }
        return condition;
    }

    private TriggerPrice stopLossLeg(CalculatedPrice price) {
        ResolvedStopLossPrice stopLoss = price.getStopLossPrice();
        if (isNull(stopLoss)) {
            return null;
        }
        TriggerPrice leg = new TriggerPrice();
        leg.setType(stopLoss.getTriggerPriceType());
        leg.setValue(stopLoss.getTriggerPrice());
        return leg;
    }

    private TriggerPrice takeProfitLeg(CalculatedPrice price) {
        ResolvedTakeProfitPrice takeProfit = price.getTakeProfitPrice();
        if (isNull(takeProfit)) {
            return null;
        }
        TriggerPrice leg = new TriggerPrice();
        leg.setType(takeProfit.getTriggerPriceType());
        leg.setValue(takeProfit.getTriggerPrice());
        return leg;
    }

    private Trailing trailingLeg(CalculatedPrice price) {
        ResolvedTrailingPrice trailing = price.getTrailingPrice();
        if (isNull(trailing)) {
            return null;
        }
        Trailing leg = new Trailing();
        leg.setTrailingPercents(trailing.getCallbackRatio());
        leg.setTrailingStepValue(trailing.getCallbackSpread());
        if (nonNull(trailing.getActivationPrice())) {
            TriggerPrice activation = new TriggerPrice();
            activation.setValue(trailing.getActivationPrice());
            leg.setActivationPrice(activation);
        }
        return leg;
    }

    private ActionPlan submitCommand(DealActionState state, DealContext dealContext) {
        Long targetEntityId = state.getTargetEntityId();
        if (isNull(targetEntityId)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.SUBMIT_ALGO_ORDER_COMMAND, dealContext, state,
                new SubmitAlgoOrderCommandPayload(targetEntityId)));
    }

    private ActionPlan refreshCommand(DealActionState state, DealContext dealContext) {
        Long targetEntityId = state.getTargetEntityId();
        if (isNull(targetEntityId)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND, dealContext, state,
                new RefreshAlgoOrderCommandPayload(targetEntityId)));
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
