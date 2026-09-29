package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.SubmitOrderCommandPayload;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Исполняет действие ВЫХОДА, объявленное шагом транша
 * (docs/components/ExitActionExecutor.md), — по одной команде за проход.
 *
 * <p><b>Выход — не одна команда.</b> Закрывающая нога гасит экспозицию, и
 * всё; в стратегии это самостоятельного смысла не имеет. Осмысленное
 * действие — выход, и отмена живых входных заявок входит в его состав, а
 * не является внешней дочисткой.
 *
 * <pre>
 * PLANNED, живые входные (не reduce-only) ноги есть → отмена этих ног
 * PLANNED, живых входных ног нет, экспозиция есть    → CREATE_ORDER_COMMAND reduce-only ноги
 * CREATED                                            → SUBMIT_ORDER_COMMAND
 * SUBMITTED                                          → REFRESH_ORDER_COMMAND
 * </pre>
 *
 * <p><b>Порядок задан инвариантом</b>
 * (docs/rules/exit-teardown-order.md): не-reduce-only нога, наливившаяся
 * ПОСЛЕ закрытия, открыла бы позицию заново — выход перестал бы быть
 * финальным ровно тогда, когда защита уже снимается.
 *
 * <p><b>Экспозицию транша гасит его собственная reduce-only нога</b>
 * размером этой экспозиции, а не закрытие позиции: команда закрытия
 * закрывает нетто-экспозицию целиком и законна только на выходе всех
 * траншей — её шлёт сворачивание сделки
 * (docs/rules/no-partial-close.md §«Две законные формы полного выхода»).
 * Нога рыночная: цена на площадку не уходит.
 *
 * <p><b>Reduce-only ноги под отмену не идут:</b> они риск снимают, а не
 * создают, и остаточные дочищает обработчик выхода транша.
 *
 * <p>Преконтроль риска здесь не зовётся: выход риск снимает.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExitActionExecutor implements StrategyActionExecutor {

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyPositionAction
                && StrategyActionType.EXIT_ACTION.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                           DealContext dealContext, DealTranche tranche) {
        return switch (state.getStatus()) {
            case PLANNED -> planExit(state, dealContext, tranche);
            case CREATED -> submit(state);
            case SUBMITTED -> refresh(state);
            default -> ActionPlan.nothing();
        };
    }

    /** Заведённая закрывающая нога — факт: повтор идёт с её отправки. */
    @Override
    public DealActionStateStatus retryStage(DealActionState state) {
        return state.creationRetryStage();
    }

    /**
     * Первая стадия выводится из подтверждённых фактов транша: сперва его
     * живые входные ноги, затем его экспозиция.
     */
    private ActionPlan planExit(DealActionState state, DealContext dealContext, DealTranche tranche) {
        List<Order> liveEntries = liveEntryLegs(tranche);
        if (isFalse(liveEntries.isEmpty())) {
            return cancel(liveEntries.get(0), state);
        }
        BigDecimal exposure = tranche.exposure();
        if (exposure.signum() <= 0) {
            return ActionPlan.nothing();
        }
        Order.Side side = dealContext.getDeal().reducingSide();
        if (isNull(side)) {
            log.warn("Deal without trade direction, tranche exit not placed dealId={} trancheId={}",
                    state.getDealId(), tranche.getId());
            return ActionPlan.nothing();
        }
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CREATE_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(closingLeg(side, exposure, tranche))
                .build());
    }

    /** Живые входные ноги транша: reduce-only ноги риск снимают и под отмену не идут. */
    private List<Order> liveEntryLegs(DealTranche tranche) {
        return emptyIfNull(tranche.liveOrders()).stream()
                .filter(order -> isTrue(order.isEntryLeg()))
                .collect(Collectors.toList());
    }

    /**
     * Отмена входной ноги — <b>дочистка без анкера</b>: бюджета отказов у
     * неё нет, и повтор ведёт сам проход
     * (docs/components/models/ServiceCommand.md).
     */
    private ActionPlan cancel(Order order, DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CANCEL_ORDER_COMMAND)
                .dealId(state.getDealId())
                .payload(new CancelOrderCommandPayload(order.getId(), Order.CloseReason.CANCELED_BY_STRATEGY))
                .build());
    }

    /**
     * Закрывающая нога транша: reduce-only размером его экспозиции,
     * рыночная. Род заявки — род простой заявки: reduce-only несёт признак
     * намерения (docs/models/domain/core/Order.md). Чисел планового риска
     * у неё нет по построению — риска она не создаёт.
     */
    private CreateOrderCommandPayload closingLeg(Order.Side side, BigDecimal exposure, DealTranche tranche) {
        return CreateOrderCommandPayload.builder()
                .orderType(Order.Type.ENTRY)
                .side(side)
                .sizeContracts(exposure)
                .sendPriceToExchange(Boolean.FALSE)
                .positionReducingOnly(Boolean.TRUE)
                .dealTrancheId(tranche.getId())
                .build();
    }

    private ActionPlan submit(DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.SUBMIT_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(new SubmitOrderCommandPayload(state.getTargetEntityId()))
                .build());
    }

    private ActionPlan refresh(DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(new RefreshOrderCommandPayload(state.getTargetEntityId()))
                .build());
    }
}
