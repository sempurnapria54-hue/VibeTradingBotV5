package com.example.tradingbot.domain.deal.handler;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.FsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса ERROR (non-terminal): обычная strategy/FSM-логика
 * заблокирована, разрешены только safety / recovery / проверка фактов.
 * Снимает live risk риск-минимизирующим порядком (закрыть позицию → отменить
 * orders → отменить algo-защиту последней), затем подтверждает факт снятия
 * через REFRESH_* (ACK не truth). Подтверждено отсутствие live risk →
 * ERROR → EMERGENCY_CLOSED (ошибочный терминал; resultProfit не блокирует,
 * DEAL-Q2). Safety-команды — без RiskValidator. См.
 * docs/components/ErrorHandler.md.
 */
@Component
@RequiredArgsConstructor
public class ErrorHandler implements FsmHandler {

    private final DealFsmSupport support;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ERROR;
    }

    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isTrue(support.positionLiveRisk(deal))
                || isNotEmpty(support.liveOrders(deal))
                || isNotEmpty(support.liveAlgoOrders(deal))) {
            return Optional.empty();
        }
        // Live risk снят и подтверждён фактами — аварийный терминал.
        return Optional.of(DealTransition.transition(Deal.Status.EMERGENCY_CLOSED));
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isTrue(support.positionLiveRisk(deal))) {
            return reduceOrConfirmPosition(dealContext, deal.getPosition());
        }
        DealTransition orderSafety = reduceOrConfirmOrders(dealContext, support.liveOrders(deal));
        if (nonNull(orderSafety)) {
            return orderSafety;
        }
        DealTransition algoSafety = reduceOrConfirmAlgoOrders(dealContext, support.liveAlgoOrders(deal));
        if (nonNull(algoSafety)) {
            return algoSafety;
        }
        return DealTransition.stay();
    }

    private DealTransition reduceOrConfirmPosition(DealContext dealContext, Position position) {
        if (isNull(position.getCloseReason())) {
            return DealTransition.command(support.closePositionCommand(dealContext, position.getId(),
                    Position.CloseReason.KILL_SWITCH));
        }
        // Закрытие уже запрошено — подтвердить flat фактами (ACK не truth).
        return DealTransition.command(support.refreshPositionCommand(dealContext));
    }

    private DealTransition reduceOrConfirmOrders(DealContext dealContext, List<Order> liveOrders) {
        if (isEmpty(liveOrders)) {
            return null;
        }
        Order order = liveOrders.getFirst();
        if (isNull(order.getCloseReason())) {
            return DealTransition.command(support.cancelOrderCommand(dealContext, order.getId(),
                    Order.CloseReason.KILL_SWITCH));
        }
        return DealTransition.command(support.refreshOrderCommand(dealContext, order.getId()));
    }

    private DealTransition reduceOrConfirmAlgoOrders(DealContext dealContext, List<AlgoOrder> liveAlgoOrders) {
        if (isEmpty(liveAlgoOrders)) {
            return null;
        }
        AlgoOrder algoOrder = liveAlgoOrders.getFirst();
        if (isNull(algoOrder.getCloseReason())) {
            return DealTransition.command(support.cancelAlgoOrderCommand(dealContext, algoOrder.getId(),
                    AlgoOrder.CloseReason.KILL_SWITCH));
        }
        return DealTransition.command(support.refreshAlgoOrderCommand(dealContext, algoOrder.getId()));
    }
}
