package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.ResolvedStopLossPrice;
import com.example.strategy.engine.calc.ResolvedTakeProfitPrice;
import com.example.strategy.engine.calc.ResolvedTrailingPrice;
import com.example.strategy.engine.calc.StrategyActionCalculationResult;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
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
import com.example.tradingcore.domain.calc.CalculationContextFactory;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.SubmitAlgoOrderCommandPayload;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Планирует создающее действие над отдельной условной заявкой
 * (docs/components/CreateAlgoOrderActionExecutor.md).
 *
 * <pre>
 * PLANNED   → расчёт → преконтроль → CREATE_ALGO_ORDER_COMMAND
 * CREATED   → SUBMIT_ALGO_ORDER_COMMAND
 * SUBMITTED → REFRESH_ALGO_ORDER_COMMAND
 * </pre>
 *
 * <p><b>Преконтроль зовётся и здесь, хотя действие reduce-only.</b> Оба
 * слагаемых акта — риск и нотинал — равны нулю, но состав неравенств от
 * этого не сужается: все четыре потолка считаются как на любом другом
 * валидируемом действии, а сверх них — предикат покрытия
 * (docs/rules/risk-validator-scope.md,
 * docs/rules/live-risk-protection.md).
 *
 * <p><b>Дерево условия собирается здесь</b>, а не в исполнителе команды:
 * какие ноги заполнять, диктует объявленный тип условия, и без него
 * защитная заявка ушла бы на площадку без триггерной цены.
 */
@Component
@RequiredArgsConstructor
public class CreateAlgoOrderActionExecutor implements StrategyActionExecutor {

    /** Типы, чья нога условия — стоп. */
    private static final Set<AlgoOrder.ConditionType> STOP_TYPES = EnumSet.of(
            AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.ConditionType.PARTIAL_STOP_LOSS);

    /** Типы, чья нога условия — тейк. */
    private static final Set<AlgoOrder.ConditionType> TAKE_TYPES = EnumSet.of(
            AlgoOrder.ConditionType.TAKE_PROFIT, AlgoOrder.ConditionType.PARTIAL_TAKE_PROFIT);

    /** Типы, ведомые трейлингом, а не триггером. */
    private static final Set<AlgoOrder.ConditionType> TRAILING_TYPES = EnumSet.of(
            AlgoOrder.ConditionType.TRAILING_PERCENTS, AlgoOrder.ConditionType.TRAILING_VALUE);

    private final CalculationContextFactory contextFactory;
    private final StrategyActionCalculator calculator;
    private final ActionRiskGate riskGate;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction
                && StrategyActionType.CREATE_ACTION.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                           DealContext dealContext, DealTranche tranche) {
        return switch (state.getStatus()) {
            case PLANNED -> planCreation((StrategyAlgoOrderAction) action, state, dealContext, tranche);
            case CREATED -> submit(state);
            case SUBMITTED -> refresh(state);
            default -> ActionPlan.nothing();
        };
    }

    private ActionPlan planCreation(StrategyAlgoOrderAction action, DealActionState state,
                                    DealContext dealContext, DealTranche tranche) {
        CalculationContext context = contextFactory.build(dealContext, action, tranche);
        StrategyActionCalculationResult result = calculator.calculate(context);
        if (isFalse(result.isSuccess())) {
            return ActionPlan.calculationFailed(result.getError());
        }
        CalculatedStrategyAction calculated = result.getCalculatedAction();
        Optional<ActionPlan> blocked = riskGate.gate(calculated, dealContext, tranche);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND)
                .dealId(dealContext.getDeal().getId())
                .dealActionStateId(state.getId())
                .payload(payload(action, calculated, context, tranche))
                .build());
    }

    private ActionPlan submit(DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.SUBMIT_ALGO_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(new SubmitAlgoOrderCommandPayload(state.getTargetEntityId()))
                .build());
    }

    private ActionPlan refresh(DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(new RefreshAlgoOrderCommandPayload(state.getTargetEntityId()))
                .build());
    }

    private CreateAlgoOrderCommandPayload payload(StrategyAlgoOrderAction action,
                                                  CalculatedStrategyAction calculated,
                                                  CalculationContext context, DealTranche tranche) {
        return CreateAlgoOrderCommandPayload.builder()
                .dealTrancheId(isNull(tranche) ? null : tranche.getId())
                .conditionType(action.getConditionType())
                .direction(closingDirection(context.getStrategyDirection()))
                .positionReducingOnly(true)
                .sizeContracts(calculated.getCalculatedSize().getSizeContracts())
                .condition(condition(action, calculated.getCalculatedPrice()))
                .build();
    }

    /**
     * Направление защиты — закрывающее к направлению сделки: защита
     * уменьшает экспозицию, а не набирает её
     * (docs/models/domain/core/AlgoOrder.md).
     */
    private AlgoOrder.Direction closingDirection(StrategyTradeDirection direction) {
        return StrategyTradeDirection.LONG.equals(direction) ? AlgoOrder.Direction.SELL : AlgoOrder.Direction.BUY;
    }

    /**
     * Дерево условия: ровно один механизм — триггер либо трейлинг, — и
     * заполненные ноги обязаны соответствовать объявленному типу
     * (docs/models/domain/core/AlgoOrder.md §Condition-модель).
     */
    private Condition condition(StrategyAlgoOrderAction action, CalculatedPrice price) {
        AlgoOrder.ConditionType type = action.getConditionType();
        Condition condition = new Condition();
        condition.setType(type);
        if (TRAILING_TYPES.contains(type)) {
            condition.setTrailing(trailing(price.getTrailingPrice()));
            return condition;
        }
        condition.setTrigger(trigger(type, price));
        return condition;
    }

    /**
     * Триггерные ноги по типу: стоп, тейк либо обе у полного OCO. Тип, чью
     * ногу расчёт не посчитал, остаётся пустым — подставить сюда чужую
     * цену значило бы выпустить защиту на уровень, которого стратегия не
     * объявляла.
     */
    private Trigger trigger(AlgoOrder.ConditionType type, CalculatedPrice price) {
        Trigger trigger = new Trigger();
        if (STOP_TYPES.contains(type) || Objects.equals(AlgoOrder.ConditionType.OCO_FULL, type)) {
            trigger.setStopLoss(stopLeg(price.getStopLossPrice()));
        }
        if (TAKE_TYPES.contains(type) || Objects.equals(AlgoOrder.ConditionType.OCO_FULL, type)) {
            trigger.setTakeProfit(takeLeg(price.getTakeProfitPrice()));
        }
        return trigger;
    }

    private TriggerPrice stopLeg(ResolvedStopLossPrice stop) {
        if (isNull(stop) || isNull(stop.getTriggerPrice())) {
            return null;
        }
        TriggerPrice leg = new TriggerPrice();
        leg.setType(stop.getTriggerPriceType());
        leg.setValue(stop.getTriggerPrice());
        return leg;
    }

    private TriggerPrice takeLeg(ResolvedTakeProfitPrice take) {
        if (isNull(take) || isNull(take.getTriggerPrice())) {
            return null;
        }
        TriggerPrice leg = new TriggerPrice();
        leg.setType(take.getTriggerPriceType());
        leg.setValue(take.getTriggerPrice());
        return leg;
    }

    /**
     * Трейлинг: доля отката и цена активации. Шаг в абсолютной величине
     * объявляется отдельным типом условия и приезжает той же долей —
     * различает их {@code conditionType}, а не форма дерева.
     */
    private Trailing trailing(ResolvedTrailingPrice resolved) {
        if (isNull(resolved)) {
            return null;
        }
        Trailing trailing = new Trailing();
        trailing.setTrailingPercents(resolved.getCallbackRatio());
        trailing.setActivationPrice(activationLeg(resolved));
        return trailing;
    }

    private TriggerPrice activationLeg(ResolvedTrailingPrice resolved) {
        BigDecimal activation = resolved.getActivationPrice();
        if (isNull(activation)) {
            return null;
        }
        TriggerPrice leg = new TriggerPrice();
        leg.setType(resolved.getTriggerPriceType());
        leg.setValue(activation);
        return leg;
    }
}
