package com.example.tradingbot.domain.service.deal.orchestrator;

import com.example.tradingbot.domain.model.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.ConditionType;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.market.MarketPhase;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.strategy.Strategy;
import com.example.tradingbot.domain.model.strategy.StrategyDetails;
import com.example.tradingbot.domain.service.DealService;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.market.MarketPhaseService;
import com.example.tradingbot.domain.service.strategy.StrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DealContextService {

    private final DealService dealService;

    private final MarketPhaseService marketPhaseService;

    private final StrategyService strategyService;


    public DealContext load(Long dealId) {
        Deal deal = dealService.getRequiredById(dealId);

        DealContext context = new DealContext();
        context.setDeal(deal);
        context.setEntryOrder(resolveEntryOrder(deal));
        context.setActivePosition(resolveActivePosition(deal));
        context.setActiveAlgoOrders(resolveActiveAlgoOrders(deal));

        Long instrumentId = deal.getInstrumentId();

        MarketPhase marketPhase = this.marketPhaseService.getMarketPhase(instrumentId);
        context.setMarketPhase(marketPhase);

        Strategy strategy = this.strategyService.getActiveStrategyRequired(instrumentId);
        context.setStrategy(strategy);

        StrategyDetails strategyDetails = resolveStrategyDetails(strategy, marketPhase);
        context.setStrategyDetails(strategyDetails);

        return context;
    }

    private Order resolveEntryOrder(Deal deal) {
        return safeList(deal.getOrders()).stream()
                                         .filter(Objects::nonNull)
                                         .filter(this::isEntryOrder)
                                         .max(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(
                                                 Comparator.naturalOrder())))
                                         .orElse(null);
    }

    private Position resolveActivePosition(Deal deal) {
        List<Position> positions = safeList(deal.getPositions());

        Position activePosition = positions.stream()
                                           .filter(Objects::nonNull)
                                           .filter(position -> position.getStatus() == Position.Status.ACTIVE)
                                           .max(Comparator.comparing(Position::getCreatedAt,
                                                                     Comparator.nullsLast(Comparator.naturalOrder())))
                                           .orElse(null);

        if (activePosition != null) {
            return activePosition;
        }

        return positions.stream()
                        .filter(Objects::nonNull)
                        .max(Comparator.comparing(Position::getCreatedAt,
                                                  Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(null);
    }

    private List<AlgoOrder> resolveActiveAlgoOrders(Deal deal) {
        return safeList(deal.getAlgoOrders()).stream()
                                             .filter(Objects::nonNull)
                                             .filter(this::isActiveAlgoOrder)
                                             .collect(Collectors.toList());
    }

    private StrategyDetails resolveStrategyDetails(Strategy strategy, MarketPhase marketPhase) {
        if (strategy == null) {
            return null;
        }

        if (strategy.getStatus() != Strategy.Status.ACTIVE) {
            return null;
        }

        if (marketPhase == null || marketPhase.getType() == null) {
            return null;
        }

        return strategy.getActiveDetails(marketPhase.getType());
    }

    private boolean isEntryOrder(Order order) {
        if (order.getType() == null) {
            return false;
        }

        return order.getType() == Order.Type.ENTRY
                || order.getType() == Order.Type.ENTRY_ATTACHED_STOP_LOSS;
    }

    private boolean isActiveAlgoOrder(AlgoOrder algoOrder) {
        if (algoOrder.getStatus() != AlgoOrder.Status.PENDING
                && algoOrder.getStatus() != AlgoOrder.Status.ACTIVE) {
            return false;
        }

        return true;
    }

    /**
     * Если позже захочешь искать именно "страховочный attached SL" из entryOrder,
     * лучше делать это derived-методом внутри DealContext, а не хранить отдельным полем.
     */
    private AttachedAlgoOrder resolveActiveAttachedStopLoss(Order entryOrder) {
        if (entryOrder == null) {
            return null;
        }

        if (entryOrder.getAttachedAlgoOrders() == null) {
            return null;
        }

        return entryOrder.getAttachedAlgoOrders()
                         .stream()
                         .filter(Objects::nonNull)
                         .filter(this::isAttachedStopLoss)
                         .filter(this::isNotClosedAttached)
                         .max(Comparator.comparing(AttachedAlgoOrder::getCreatedAt,
                                                   Comparator.nullsLast(Comparator.naturalOrder())))
                         .orElse(null);
    }

    private boolean isAttachedStopLoss(AttachedAlgoOrder attachedAlgoOrder) {
        if (attachedAlgoOrder.getType() == null) {
            return false;
        }

        return attachedAlgoOrder.getType() == AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS;
    }

    private boolean isNotClosedAttached(AttachedAlgoOrder attachedAlgoOrder) {
        return attachedAlgoOrder.getStatus() != AttachedAlgoOrder.Status.CLOSED
                && attachedAlgoOrder.getStatus() != AttachedAlgoOrder.Status.FAILED;
    }

    private boolean hasMainStopProtection(List<AlgoOrder> activeAlgoOrders) {
        if (activeAlgoOrders == null || activeAlgoOrders.isEmpty()) {
            return false;
        }

        return activeAlgoOrders.stream()
                               .filter(Objects::nonNull)
                               .anyMatch(algoOrder -> algoOrder.getConditionType() == ConditionType.STOP_LOSS);
    }

    private <T> List<T> safeList(List<T> source) {
        if (source == null) {
            return List.of();
        }

        return source;
    }
}