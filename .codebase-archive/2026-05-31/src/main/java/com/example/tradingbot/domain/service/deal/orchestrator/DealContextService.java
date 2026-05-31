package com.example.tradingbot.domain.service.deal.orchestrator;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.ConditionType;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.market.MarketPhase;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.strategy.Strategy;
import com.example.tradingbot.domain.model.trade.strategy.StrategyDetails;
import com.example.tradingbot.domain.service.core.DealService;
import com.example.tradingbot.domain.service.core.ExchangeService;
import com.example.tradingbot.domain.service.core.InstrumentService;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.domain.service.market.MarketPhaseService;
import com.example.tradingbot.domain.service.strategy.StrategyService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
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
    private final InstrumentService instrumentService;
    private final ExchangeService exchangeService;

    public DealContext load(Long dealId) {
        Deal deal = dealService.getRequiredById(dealId);
        Instrument instrument = instrumentService.getRequiredById(deal.getInstrumentId());
        Exchange exchange = exchangeService.getRequiredById(instrument.getExchangeId());

        DealContext context = new DealContext();
        context.setDeal(deal);
        context.setExchange(exchange);
        context.setInstrument(instrument);
        context.setOrders(safeList(deal.getOrders()));
        context.setAlgoOrders(safeList(deal.getAlgoOrders()));
        context.setEntryOrder(resolveEntryOrder(deal));
        context.setActivePosition(resolveActivePosition(deal));
        context.setActiveAlgoOrders(resolveActiveAlgoOrders(deal));

        MarketPhase marketPhase = this.marketPhaseService.getMarketPhase(instrument.getId());
        context.setMarketPhase(marketPhase);

        Strategy strategy = this.strategyService.getActiveStrategyRequired(instrument.getId());
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
                                           .filter(position -> Objects.equals(position.getStatus(),
                                                                              Position.Status.ACTIVE))
                                           .max(Comparator.comparing(Position::getCreatedAt,
                                                                     Comparator.nullsLast(Comparator.naturalOrder())))
                                           .orElse(null);

        if (Objects.nonNull(activePosition)) {
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
        if (Objects.isNull(strategy)) {
            return null;
        }

        if (strategy.isNotActive()) {
            return null;
        }

        if (Objects.isNull(marketPhase) || Objects.isNull(marketPhase.getType())) {
            return null;
        }

        return strategy.getActiveDetails(marketPhase.getType());
    }

    private boolean isEntryOrder(Order order) {
        if (Objects.isNull(order.getType())) {
            return false;
        }

        return Objects.equals(order.getType(), Order.Type.ENTRY)
                || Objects.equals(order.getType(), Order.Type.ENTRY_ATTACHED_STOP_LOSS);
    }

    private boolean isActiveAlgoOrder(AlgoOrder algoOrder) {
        if (Objects.isNull(algoOrder.getStatus())) {
            return false;
        }

        return Objects.equals(algoOrder.getStatus(), AlgoOrder.Status.PENDING)
                || Objects.equals(algoOrder.getStatus(), AlgoOrder.Status.ACTIVE);
    }

    /**
     * Если позже захочешь искать именно "страховочный attached SL" из entryOrder,
     * лучше делать это derived-методом внутри DealContext, а не хранить отдельным полем.
     */
    private AttachedAlgoOrder resolveActiveAttachedStopLoss(Order entryOrder) {
        if (Objects.isNull(entryOrder)) {
            return null;
        }

        if (Objects.isNull(entryOrder.getAttachedAlgoOrders())) {
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
        if (Objects.isNull(attachedAlgoOrder.getType())) {
            return false;
        }

        return Objects.equals(attachedAlgoOrder.getType(), AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
    }

    private boolean isNotClosedAttached(AttachedAlgoOrder attachedAlgoOrder) {
        return BooleanUtils.isFalse(Objects.equals(attachedAlgoOrder.getStatus(), AttachedAlgoOrder.Status.CLOSED))
                && BooleanUtils.isFalse(Objects.equals(attachedAlgoOrder.getStatus(), AttachedAlgoOrder.Status.FAILED));
    }

    private boolean hasMainStopProtection(List<AlgoOrder> activeAlgoOrders) {
        if (Objects.isNull(activeAlgoOrders) || activeAlgoOrders.isEmpty()) {
            return false;
        }

        return activeAlgoOrders.stream()
                               .filter(Objects::nonNull)
                               .anyMatch(algoOrder -> Objects.equals(algoOrder.getConditionType(),
                                                                     ConditionType.STOP_LOSS));
    }

    private <T> List<T> safeList(List<T> source) {
        if (Objects.isNull(source)) {
            return List.of();
        }

        return source;
    }
}
