package com.example.tradingbot.domain.service.strategy.interpreter;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.ServiceCommandType;
import com.example.tradingbot.domain.model.commands.payload.AmendAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.AmendOrderCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.CancelOrderCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.ClosePositionCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.CreateOrderCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.SubmitAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.commands.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.ConditionType;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyActionType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.model.trade.strategy.StrategyOrderAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyPositionAction;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStep;
import com.example.tradingbot.domain.model.trade.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.trade.strategy.StrategyTradeDirection;
import com.example.tradingbot.domain.service.deal.command.core.ServiceCommandFactory;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.strategy.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.service.strategy.price.ResolvedAlgoOrderPrice;
import com.example.tradingbot.domain.service.strategy.price.ResolvedAttachedProtectionPrice;
import com.example.tradingbot.domain.service.strategy.price.ResolvedOrderPrice;
import com.example.tradingbot.domain.service.strategy.price.StrategyPriceResolver;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Service
@RequiredArgsConstructor
public class StrategyActionInterpreter {

    private static final BigDecimal DEFAULT_SIZE = BigDecimal.ONE;

    private final StrategyConditionEvaluator conditionEvaluator;

    private final StrategyPriceResolver priceResolver;

    private final ServiceCommandFactory commandFactory;

    public List<ServiceCommand> interpret(DealContext context, Set<StrategyStepType> allowedStepTypes) {
        if (Objects.isNull(context) || Objects.isNull(context.getDeal())) {
            return List.of();
        }

        StrategyDetails strategyDetails = context.getStrategyDetails();
        if (Objects.isNull(strategyDetails) || Objects.isNull(strategyDetails.getStepsByStatus())) {
            return List.of();
        }

        List<StrategyStep> steps = strategyDetails.getStepsByStatus()
                .get(context.getDeal().getStatus());
        if (emptyIfNull(steps).isEmpty()) {
            return List.of();
        }

        List<ServiceCommand> commands = new ArrayList<>();
        int stepIndex = 0;
        for (StrategyStep step : emptyIfNull(steps)) {
            stepIndex = stepIndex + 1;
            if (isStepAllowed(step, allowedStepTypes)
                    && conditionEvaluator.evaluate(step.getCondition(), context)) {
                appendStepCommands(commands, context, step, stepIndex);
            }
        }

        return commands;
    }

    private boolean isStepAllowed(StrategyStep step, Set<StrategyStepType> allowedStepTypes) {
        if (Objects.isNull(step) || Objects.isNull(step.getStepType())) {
            return false;
        }
        if (emptyIfNull(allowedStepTypes).isEmpty()) {
            return false;
        }
        return allowedStepTypes.contains(step.getStepType());
    }

    private void appendStepCommands(List<ServiceCommand> commands,
                                    DealContext context,
                                    StrategyStep step,
                                    int stepIndex) {
        int actionIndex = 0;
        for (StrategyAction action : emptyIfNull(step.getActions())) {
            actionIndex = actionIndex + 1;
            if (Objects.isNull(action)) {
                continue;
            }

            Long strategyActionId = resolveStrategyActionId(context, action, stepIndex, actionIndex);
            ServiceCommand command = toCommand(context, step.getStepType(), action, strategyActionId);
            if (Objects.nonNull(command)) {
                commands.add(command);
            }
        }
    }

    private Long resolveStrategyActionId(DealContext context, StrategyAction action, int stepIndex, int actionIndex) {
        if (Objects.nonNull(action.getId())) {
            return action.getId();
        }

        long statusIndex = Objects.nonNull(context.getDeal().getStatus()) ? context.getDeal().getStatus().ordinal() : 0L;
        long generatedId = statusIndex * 1_000_000L + (long) stepIndex * 1_000L + actionIndex;
        action.setId(generatedId);
        return generatedId;
    }

    private ServiceCommand toCommand(DealContext context,
                                     StrategyStepType stepType,
                                     StrategyAction action,
                                     Long strategyActionId) {
        if (action instanceof StrategyOrderAction orderAction) {
            return toOrderCommand(context, stepType, orderAction, strategyActionId);
        }
        if (action instanceof StrategyAlgoOrderAction algoOrderAction) {
            return toAlgoOrderCommand(context, stepType, algoOrderAction, strategyActionId);
        }
        if (action instanceof StrategyPositionAction positionAction) {
            return toPositionCommand(context, stepType, positionAction, strategyActionId);
        }
        return null;
    }

    private ServiceCommand toOrderCommand(DealContext context,
                                          StrategyStepType stepType,
                                          StrategyOrderAction action,
                                          Long strategyActionId) {
        StrategyActionType actionType = action.getActionType();
        if (Objects.equals(actionType, StrategyActionType.CREATE)) {
            return toOrderCreateFlowCommand(context, stepType, action, strategyActionId);
        }
        if (Objects.equals(actionType, StrategyActionType.AMEND)) {
            return toAmendOrderCommand(context, stepType, action, strategyActionId);
        }
        if (Objects.equals(actionType, StrategyActionType.CANCEL)) {
            return toCancelOrderCommand(context, stepType, action, strategyActionId);
        }
        return null;
    }

    private ServiceCommand toOrderCreateFlowCommand(DealContext context,
                                                    StrategyStepType stepType,
                                                    StrategyOrderAction action,
                                                    Long strategyActionId) {
        Order order = context.findOrderByStrategyActionId(strategyActionId);
        if (Objects.isNull(order)) {
            CreateOrderCommandPayload payload = CreateOrderCommandPayload.builder()
                    .strategyActionId(strategyActionId)
                    .orderType(action.getOrderType())
                    .side(resolveOrderSide(action))
                    .price(resolveOrderPrice(action, context))
                    .size(DEFAULT_SIZE)
                    .attachedAlgoOrders(resolveAttachedProtection(action, context))
                    .build();
            return commandFactory.strategy(context,
                                           ServiceCommandType.CREATE_ORDER,
                                           stepType,
                                           action,
                                           strategyActionId,
                                           payload);
        }

        if (isCreatedWithoutExternal(order)) {
            SubmitOrderCommandPayload payload = SubmitOrderCommandPayload.builder()
                    .orderId(order.getId())
                    .strategyActionId(strategyActionId)
                    .build();
            return commandFactory.strategy(context,
                                           ServiceCommandType.SUBMIT_ORDER,
                                           stepType,
                                           action,
                                           strategyActionId,
                                           payload);
        }

        if (Objects.nonNull(order.getExternalId()) && order.isLive()) {
            return commandFactory.strategy(context,
                                           ServiceCommandType.REFRESH_ORDER,
                                           stepType,
                                           action,
                                           strategyActionId,
                                           null);
        }

        return null;
    }

    private ServiceCommand toAmendOrderCommand(DealContext context,
                                               StrategyStepType stepType,
                                               StrategyOrderAction action,
                                               Long strategyActionId) {
        Order order = context.findOrderByStrategyActionId(strategyActionId);
        if (Objects.isNull(order)) {
            return null;
        }

        AmendOrderCommandPayload payload = AmendOrderCommandPayload.builder()
                .orderId(order.getId())
                .strategyActionId(strategyActionId)
                .price(resolveOrderPrice(action, context))
                .size(resolveCurrentOrderSize(order))
                .build();
        return commandFactory.strategy(context,
                                       ServiceCommandType.AMEND_ORDER,
                                       stepType,
                                       action,
                                       strategyActionId,
                                       payload);
    }

    private ServiceCommand toCancelOrderCommand(DealContext context,
                                                StrategyStepType stepType,
                                                StrategyOrderAction action,
                                                Long strategyActionId) {
        Order order = context.findOrderByStrategyActionId(strategyActionId);
        if (Objects.isNull(order)) {
            return null;
        }

        CancelOrderCommandPayload payload = CancelOrderCommandPayload.builder()
                .orderId(order.getId())
                .strategyActionId(strategyActionId)
                .build();
        return commandFactory.strategy(context,
                                       ServiceCommandType.CANCEL_ORDER,
                                       stepType,
                                       action,
                                       strategyActionId,
                                       payload);
    }

    private ServiceCommand toAlgoOrderCommand(DealContext context,
                                              StrategyStepType stepType,
                                              StrategyAlgoOrderAction action,
                                              Long strategyActionId) {
        StrategyActionType actionType = action.getActionType();
        if (Objects.equals(actionType, StrategyActionType.CREATE)) {
            return toAlgoOrderCreateFlowCommand(context, stepType, action, strategyActionId);
        }
        if (Objects.equals(actionType, StrategyActionType.AMEND)) {
            return toAmendAlgoOrderCommand(context, stepType, action, strategyActionId);
        }
        if (Objects.equals(actionType, StrategyActionType.CANCEL)) {
            return toCancelAlgoOrderCommand(context, stepType, action, strategyActionId);
        }
        return null;
    }

    private ServiceCommand toAlgoOrderCreateFlowCommand(DealContext context,
                                                        StrategyStepType stepType,
                                                        StrategyAlgoOrderAction action,
                                                        Long strategyActionId) {
        AlgoOrder algoOrder = context.findAlgoOrderByStrategyActionId(strategyActionId);
        if (Objects.isNull(algoOrder)) {
            Condition condition = resolveAlgoOrderCondition(action, context);
            CreateAlgoOrderCommandPayload payload = CreateAlgoOrderCommandPayload.builder()
                    .strategyActionId(strategyActionId)
                    .conditionType(action.getConditionType())
                    .size(DEFAULT_SIZE)
                    .direction(resolveAlgoOrderDirection(context))
                    .externalType(resolveAlgoOrderExternalType(action.getConditionType()))
                    .externalDirection(resolveAlgoOrderExternalDirection(context))
                    .condition(condition)
                    .build();
            return commandFactory.strategy(context,
                                           ServiceCommandType.CREATE_ALGO_ORDER,
                                           stepType,
                                           action,
                                           strategyActionId,
                                           payload);
        }

        if (isCreatedWithoutExternal(algoOrder)) {
            SubmitAlgoOrderCommandPayload payload = SubmitAlgoOrderCommandPayload.builder()
                    .algoOrderId(algoOrder.getId())
                    .strategyActionId(strategyActionId)
                    .build();
            return commandFactory.strategy(context,
                                           ServiceCommandType.SUBMIT_ALGO_ORDER,
                                           stepType,
                                           action,
                                           strategyActionId,
                                           payload);
        }

        if (Objects.nonNull(algoOrder.getExternalId()) && algoOrder.isLive()) {
            return commandFactory.strategy(context,
                                           ServiceCommandType.REFRESH_ALGO_ORDER,
                                           stepType,
                                           action,
                                           strategyActionId,
                                           null);
        }

        return null;
    }

    private ServiceCommand toAmendAlgoOrderCommand(DealContext context,
                                                   StrategyStepType stepType,
                                                   StrategyAlgoOrderAction action,
                                                   Long strategyActionId) {
        AlgoOrder algoOrder = context.findAlgoOrderByStrategyActionId(strategyActionId);
        if (Objects.isNull(algoOrder)) {
            return null;
        }

        AmendAlgoOrderCommandPayload payload = AmendAlgoOrderCommandPayload.builder()
                .algoOrderId(algoOrder.getId())
                .strategyActionId(strategyActionId)
                .size(resolveCurrentAlgoOrderSize(algoOrder))
                .condition(resolveAlgoOrderCondition(action, context))
                .build();
        return commandFactory.strategy(context,
                                       ServiceCommandType.AMEND_ALGO_ORDER,
                                       stepType,
                                       action,
                                       strategyActionId,
                                       payload);
    }

    private ServiceCommand toCancelAlgoOrderCommand(DealContext context,
                                                    StrategyStepType stepType,
                                                    StrategyAlgoOrderAction action,
                                                    Long strategyActionId) {
        AlgoOrder algoOrder = context.findAlgoOrderByStrategyActionId(strategyActionId);
        if (Objects.isNull(algoOrder)) {
            return null;
        }

        CancelAlgoOrderCommandPayload payload = CancelAlgoOrderCommandPayload.builder()
                .algoOrderId(algoOrder.getId())
                .strategyActionId(strategyActionId)
                .build();
        return commandFactory.strategy(context,
                                       ServiceCommandType.CANCEL_ALGO_ORDER,
                                       stepType,
                                       action,
                                       strategyActionId,
                                       payload);
    }

    private ServiceCommand toPositionCommand(DealContext context,
                                             StrategyStepType stepType,
                                             StrategyPositionAction action,
                                             Long strategyActionId) {
        StrategyActionType actionType = action.getActionType();
        if (BooleanUtils.isFalse(Objects.equals(actionType, StrategyActionType.CLOSE_FULL))
                && BooleanUtils.isFalse(Objects.equals(actionType, StrategyActionType.CLOSE_PARTIAL))) {
            return null;
        }

        Position activePosition = context.getActivePosition();
        ClosePositionCommandPayload payload = ClosePositionCommandPayload.builder()
                .positionId(Objects.nonNull(activePosition) ? activePosition.getId() : null)
                .closeFractionPercents(action.getCloseFractionPercents())
                .build();
        return commandFactory.strategy(context,
                                       ServiceCommandType.CLOSE_POSITION,
                                       stepType,
                                       action,
                                       strategyActionId,
                                       payload);
    }

    private boolean isCreatedWithoutExternal(Order order) {
        return Objects.equals(order.getStatus(), Order.Status.CREATED)
                && Objects.isNull(order.getExternalId());
    }

    private boolean isCreatedWithoutExternal(AlgoOrder algoOrder) {
        return Objects.equals(algoOrder.getStatus(), AlgoOrder.Status.CREATED)
                && Objects.isNull(algoOrder.getExternalId());
    }

    private String resolveOrderSide(StrategyOrderAction action) {
        if (Objects.equals(action.getDirection(), StrategyTradeDirection.SHORT)) {
            return "sell";
        }
        return "buy";
    }

    private BigDecimal resolveOrderPrice(StrategyOrderAction action, DealContext context) {
        ResolvedOrderPrice price = priceResolver.resolveOrderPrice(action, context);
        if (Objects.isNull(price)) {
            return null;
        }
        return price.price();
    }

    private List<AttachedAlgoOrder> resolveAttachedProtection(StrategyOrderAction action, DealContext context) {
        StrategyAttachedProtectionSettings settings = action.getAttachedProtection();
        if (Objects.isNull(settings)) {
            return List.of();
        }

        ResolvedAttachedProtectionPrice price = priceResolver.resolveAttachedProtectionPrice(settings, context);
        AttachedAlgoOrder attachedAlgoOrder = new AttachedAlgoOrder();
        attachedAlgoOrder.setType(resolveAttachedType(settings));
        attachedAlgoOrder.setExternalType("attachAlgoOrds");
        attachedAlgoOrder.setSize(DEFAULT_SIZE);
        attachedAlgoOrder.setStopLossTriggerPrice(Objects.nonNull(price) ? price.stopLossTriggerPrice() : null);
        return List.of(attachedAlgoOrder);
    }

    private AttachedAlgoOrder.Type resolveAttachedType(StrategyAttachedProtectionSettings settings) {
        if (Objects.nonNull(settings.getAttachedType())) {
            return settings.getAttachedType();
        }
        return AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS;
    }

    private Condition resolveAlgoOrderCondition(StrategyAlgoOrderAction action, DealContext context) {
        ResolvedAlgoOrderPrice price = priceResolver.resolveAlgoOrderPrice(action, context);
        if (Objects.isNull(price)) {
            return null;
        }
        return price.condition();
    }

    private AlgoOrder.Direction resolveAlgoOrderDirection(DealContext context) {
        if (isShortPosition(context)) {
            return AlgoOrder.Direction.BUY;
        }
        return AlgoOrder.Direction.SELL;
    }

    private String resolveAlgoOrderExternalDirection(DealContext context) {
        if (Objects.equals(resolveAlgoOrderDirection(context), AlgoOrder.Direction.BUY)) {
            return "buy";
        }
        return "sell";
    }

    private boolean isShortPosition(DealContext context) {
        Position activePosition = context.getActivePosition();
        if (Objects.isNull(activePosition)) {
            return false;
        }
        return Objects.equals(activePosition.getSide(), Position.Side.SHORT)
                || Objects.equals(activePosition.getExternalSide(), "short");
    }

    private String resolveAlgoOrderExternalType(ConditionType conditionType) {
        if (Objects.equals(conditionType, ConditionType.OCO_FULL)) {
            return "oco";
        }
        if (Objects.equals(conditionType, ConditionType.TRAILING_PERCENTS)
                || Objects.equals(conditionType, ConditionType.TRAILING_VALUE)) {
            return "move_order_stop";
        }
        return "conditional";
    }

    private BigDecimal resolveCurrentOrderSize(Order order) {
        if (Objects.nonNull(order.getSize())) {
            return order.getSize();
        }
        return DEFAULT_SIZE;
    }

    private BigDecimal resolveCurrentAlgoOrderSize(AlgoOrder algoOrder) {
        if (Objects.nonNull(algoOrder.getSize())) {
            return algoOrder.getSize();
        }
        return DEFAULT_SIZE;
    }
}
