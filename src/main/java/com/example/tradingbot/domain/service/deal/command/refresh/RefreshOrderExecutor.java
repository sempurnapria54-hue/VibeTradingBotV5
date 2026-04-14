package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.service.validator.TradeRuleValidator;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshOrderExecutor {

    private final ClientManager clientManager;
    private final OrderDataService orderDataService;
    private final TradeRuleValidator tradeRuleValidator;
    private final OrderMapper mapper;
    private final DealDataService dealDataService;
    private final OrderStatusResolver orderStatusResolver;
    private final RefreshAttachedAlgoOrderExecutor refreshAttachedAlgoOrderExecutor;

    @Transactional
    public void execute(Exchange exchange, Instrument instrument) {
        List<OrderExternalSnapshot> pendingSnapshots = getPendingSnapshots(exchange, instrument);
        List<Order> localLiveOrders = orderDataService.findAllByInstrumentIdAndStatuses(
                instrument.getId(),
                Set.of(Order.Status.CREATED.name(),
                       Order.Status.PENDING.name(),
                       Order.Status.ACTIVE.name(),
                       Order.Status.PARTIALLY_COMPLETED.name())
        );

        tradeRuleValidator.validateRefreshOrders(exchange, instrument, pendingSnapshots, localLiveOrders);

        Set<Long> handledLocalIds = new HashSet<>();
        for (OrderExternalSnapshot snapshot : pendingSnapshots) {
            Order order = matchLocalOrder(localLiveOrders, snapshot).orElseGet(() -> createUnknownOrder(snapshot,
                                                                                                          instrument
                                                                                                                  .getId()));
            applySnapshot(order, snapshot);
            order = orderDataService.save(order);
            refreshAttachedAlgoOrderExecutor.execute(order, snapshot);
            if (order.getId() != null) {
                handledLocalIds.add(order.getId());
            }
        }

        List<OrderExternalSnapshot> historySnapshots = clientManager.getClientService(exchange.getName())
                                                                    .getOrdersHistory(instrument);
        List<OrderExternalSnapshot> archiveSnapshots = clientManager.getClientService(exchange.getName())
                                                                    .getOrdersHistoryArchive(instrument);

        for (Order localLiveOrder : localLiveOrders) {
            if (localLiveOrder.getId() != null && handledLocalIds.contains(localLiveOrder.getId())) {
                continue;
            }
            OrderExternalSnapshot recovered = recoverFinalSnapshot(exchange,
                                                                   instrument,
                                                                   localLiveOrder,
                                                                   historySnapshots,
                                                                   archiveSnapshots);
            applySnapshot(localLiveOrder, recovered);
            localLiveOrder = orderDataService.save(localLiveOrder);
            refreshAttachedAlgoOrderExecutor.execute(localLiveOrder, recovered);
        }
    }

    private List<OrderExternalSnapshot> getPendingSnapshots(Exchange exchange, Instrument instrument) {
        List<OrderExternalSnapshot> snapshots = clientManager.getClientService(exchange.getName())
                                                             .getActiveOrdersByInstrument(instrument);
        return snapshots == null ? List.of() : snapshots;
    }

    private Optional<Order> matchLocalOrder(List<Order> localLiveOrders, OrderExternalSnapshot snapshot) {
        return localLiveOrders.stream()
                              .filter(order -> isMatch(order, snapshot))
                              .findFirst();
    }

    private boolean isMatch(Order order, OrderExternalSnapshot snapshot) {
        return Objects.equals(order.getExternalId(), snapshot.getExternalId())
                || Objects.equals(order.getInternalId(), snapshot.getInternalId());
    }

    private void applySnapshot(Order order, OrderExternalSnapshot snapshot) {
        mapper.updateDomainFromExternalSnapshot(snapshot, order);
        order.setStatus(orderStatusResolver.resolve(snapshot.getExternalStatus()));
    }

    private Order createUnknownOrder(OrderExternalSnapshot snapshot, Long instrumentId) {
        Deal deal = dealDataService.findLatestByInstrumentId(instrumentId)
                                   .orElseThrow(() -> new IllegalStateException(
                                           "Deal is missing for instrument: " + instrumentId));
        Order order = new Order();
        order.setDealId(deal.getId());
        order.setInternalId(resolveInternalId(snapshot));
        order.setAttachedAlgoOrders(new ArrayList<>());
        applySnapshot(order, snapshot);
        return order;
    }

    private String resolveInternalId(OrderExternalSnapshot snapshot) {
        if (snapshot.getInternalId() != null && !snapshot.getInternalId().isBlank()) {
            return snapshot.getInternalId();
        }
        return UUID.randomUUID().toString();
    }

    private OrderExternalSnapshot recoverFinalSnapshot(Exchange exchange,
                                                       Instrument instrument,
                                                       Order order,
                                                       List<OrderExternalSnapshot> history,
                                                       List<OrderExternalSnapshot> archive) {
        OrderExternalSnapshot detail = tryGetOrderDetail(exchange, instrument, order);
        if (detail != null) {
            return detail;
        }

        OrderExternalSnapshot fromHistory = findByIdentity(history, order);
        if (fromHistory != null) {
            return fromHistory;
        }

        OrderExternalSnapshot fromArchive = findByIdentity(archive, order);
        if (fromArchive != null) {
            return fromArchive;
        }

        throw new IllegalStateException("Unable to recover final order snapshot for order: " + order.getInternalId());
    }

    private OrderExternalSnapshot tryGetOrderDetail(Exchange exchange, Instrument instrument, Order order) {
        try {
            return clientManager.getClientService(exchange.getName())
                                .getOrder(instrument.getExternalId(), order.getExternalId(), order.getInternalId());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private OrderExternalSnapshot findByIdentity(List<OrderExternalSnapshot> snapshots, Order order) {
        if (snapshots == null) {
            return null;
        }
        return snapshots.stream()
                        .filter(snapshot -> Objects.equals(snapshot.getExternalId(), order.getExternalId())
                                || Objects.equals(snapshot.getInternalId(), order.getInternalId()))
                        .findFirst()
                        .orElse(null);
    }
}
