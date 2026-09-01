package com.example.tradingbot.domain.deal.tranche;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.TrancheTransition;
import com.example.tradingbot.domain.deal.TrancheFsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
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
 * docs/components/TrancheExitPendingHandler.md.
 */
@Component
@RequiredArgsConstructor
public class TrancheExitPendingHandler implements TrancheFsmHandler {

    private final DealFsmSupport support;

    @Override
    public DealTranche.Status supportedStatus() {
        return DealTranche.Status.EXIT_PENDING;
    }

    @Override
    public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
        Deal deal = dealContext.getDeal();
        if (isTrue(support.positionLiveRisk(deal))) {
            return TrancheTransition.command(support.closePositionCommand(dealContext, deal.getPosition().getId(),
                    Position.CloseReason.CLOSED_BY_STRATEGY));
        }
        List<Order> liveOrders = support.liveOrders(deal);
        if (isNotEmpty(liveOrders)) {
            return TrancheTransition.command(support.cancelOrderCommand(dealContext, liveOrders.getFirst().getId(),
                    Order.CloseReason.CANCELED_BY_STRATEGY));
        }
        List<AlgoOrder> liveAlgoOrders = support.liveAlgoOrders(deal);
        if (isNotEmpty(liveAlgoOrders)) {
            return TrancheTransition.command(support.cancelAlgoOrderCommand(dealContext, liveAlgoOrders.getFirst().getId(),
                    AlgoOrder.CloseReason.CANCELED_BY_STRATEGY));
        }
        if (isFalse(support.balanceUsable(dealContext))) {
            return TrancheTransition.command(support.refreshBalanceCommand(dealContext));
        }
        // Финализация исчерпала повторы (FAILED) — на ошибочную тропу (DEAL-Q2): доходим до терминала.
        if (isTrue(support.finalizationFailed(dealContext, DealFinalizationType.FINALIZE_EXIT))
                || isTrue(support.finalizationFailed(dealContext, DealFinalizationType.MARK_CLOSED))) {
            return TrancheTransition.escalateToDealError();
        }
        // Live risk снят, хвостов нет, баланс есть — консолидировать выход и закрыть.
        return support.finalizationCommand(DealFinalizationType.FINALIZE_EXIT, dealContext)
                .map(TrancheTransition::command)
                .orElseGet(() -> support.finalizationCommand(DealFinalizationType.MARK_CLOSED, dealContext)
                        .map(TrancheTransition::command)
                        .orElseGet(TrancheTransition::stay));
    }
}
