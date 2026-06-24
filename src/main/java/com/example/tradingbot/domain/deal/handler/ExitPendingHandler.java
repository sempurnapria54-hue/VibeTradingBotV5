package com.example.tradingbot.domain.deal.handler;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.FsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса EXIT_PENDING: дочищает сделку после инициированного
 * выхода. Порядок риск-минимизирующий: закрыть живую позицию → отменить
 * live orders → отменить live algo (защита снимается последней) → обновить
 * баланс → FINALIZE_DEAL_EXIT → MARK_DEAL_CLOSED (терминал CLOSED). Cleanup
 * команды — без RiskValidator (docs/rules/risk-validator-scope.md). Терминал
 * ставит MarkDealClosedExecutor по подтверждённому отсутствию live risk. См.
 * docs/components/ExitPendingHandler.md.
 */
@Component
@RequiredArgsConstructor
public class ExitPendingHandler implements FsmHandler {

    private final DealFsmSupport support;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.EXIT_PENDING;
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isTrue(support.positionLiveRisk(deal))) {
            return DealTransition.command(support.closePositionCommand(dealContext, deal.getPosition().getId(),
                    Position.CloseReason.CLOSED_BY_STRATEGY));
        }
        List<Order> liveOrders = support.liveOrders(deal);
        if (isNotEmpty(liveOrders)) {
            return DealTransition.command(support.cancelOrderCommand(dealContext, liveOrders.getFirst().getId(),
                    Order.CloseReason.CANCELED_BY_STRATEGY));
        }
        List<AlgoOrder> liveAlgoOrders = support.liveAlgoOrders(deal);
        if (isNotEmpty(liveAlgoOrders)) {
            return DealTransition.command(support.cancelAlgoOrderCommand(dealContext, liveAlgoOrders.getFirst().getId(),
                    AlgoOrder.CloseReason.CANCELED_BY_STRATEGY));
        }
        if (isFalse(support.balanceUsable(dealContext))) {
            return DealTransition.command(support.refreshBalanceCommand(dealContext));
        }
        // Финализация исчерпала повторы (FAILED) — на ошибочную тропу (DEAL-Q2): доходим до терминала.
        if (isTrue(support.finalizationFailed(dealContext, DealFinalizationType.FINALIZE_EXIT))
                || isTrue(support.finalizationFailed(dealContext, DealFinalizationType.MARK_CLOSED))) {
            return support.markError(dealContext);
        }
        // Live risk снят, хвостов нет, баланс есть — консолидировать выход и закрыть.
        return support.finalizationCommand(DealFinalizationType.FINALIZE_EXIT, dealContext)
                .map(DealTransition::command)
                .orElseGet(() -> support.finalizationCommand(DealFinalizationType.MARK_CLOSED, dealContext)
                        .map(DealTransition::command)
                        .orElseGet(DealTransition::stay));
    }
}
