package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.service.validator.TradeRuleValidator;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
@RequiredArgsConstructor
public class RefreshOrderExecutor {

    private static final Set<String> LIVE_ORDER_STATUSES = Set.of(
            Order.Status.CREATED.name(),
            Order.Status.PENDING.name(),
            Order.Status.ACTIVE.name(),
            Order.Status.PARTIALLY_COMPLETED.name()
    );

    private final ClientManager clientManager;
    private final OrderDataService orderDataService;
    private final TradeRuleValidator tradeRuleValidator;
    private final OrderMapper mapper;
    private final OrderStatusResolver orderStatusResolver;
    private final RefreshAttachedAlgoOrderExecutor refreshAttachedAlgoOrderExecutor;

    @Transactional
    public void execute(Exchange exchange, Instrument instrument, Long dealId) {
        ClientService clientService = clientManager.getClientService(exchange.getName());

        List<OrderExternalSnapshot> externalPendingOrders = getPendingSnapshots(clientService, instrument);
        List<Order> internalLiveOrders = orderDataService.findAllByInstrumentIdAndStatuses(
                instrument.getId(),
                LIVE_ORDER_STATUSES
        );

        tradeRuleValidator.validateRefreshPendingOrders(exchange,
                                                        instrument,
                                                        dealId,
                                                        externalPendingOrders,
                                                        internalLiveOrders);

        Set<Long> handledLocalIds = new HashSet<>();
        for (OrderExternalSnapshot snapshot : externalPendingOrders) {
            Order matchedOrder = matchLocalOrder(internalLiveOrders, snapshot)
                    .orElseThrow(() -> new IllegalStateException(
                            "Validated snapshot has no local live order mapping: " + snapshot.getExternalId()));
            applySnapshot(matchedOrder, snapshot);
            Order savedOrder = orderDataService.save(matchedOrder);
            refreshAttachedAlgoOrderExecutor.refreshAttachedAlgoOrders(savedOrder, snapshot);
            if (Objects.nonNull(savedOrder.getId())) {
                handledLocalIds.add(savedOrder.getId());
            }
        }

        List<Order> missingFromPending = internalLiveOrders.stream()
                                                           .filter(order -> Objects.isNull(order.getId())
                                                                   || isFalse(handledLocalIds.contains(order.getId())))
                                                           .toList();
        if (CollectionUtils.isEmpty(missingFromPending)) {
            return;
        }

        List<OrderExternalSnapshot> historySnapshots = new ArrayList<>(safeList(clientService.getOrdersHistory(
                instrument)));
        List<OrderExternalSnapshot> archiveSnapshots = new ArrayList<>(safeList(clientService.getOrdersHistoryArchive(
                instrument)));

        for (Order localLiveOrder : missingFromPending) {
            OrderExternalSnapshot recoveredSnapshot = recoverFinalSnapshot(clientService,
                                                                           instrument,
                                                                           localLiveOrder,
                                                                           historySnapshots,
                                                                           archiveSnapshots);
            applySnapshot(localLiveOrder, recoveredSnapshot);
            Order savedOrder = orderDataService.save(localLiveOrder);
            refreshAttachedAlgoOrderExecutor.refreshAttachedAlgoOrders(savedOrder, recoveredSnapshot);
        }
    }

    private List<OrderExternalSnapshot> getPendingSnapshots(ClientService clientService, Instrument instrument) {
        return safeList(clientService.getActiveOrdersByInstrument(instrument));
    }

    private List<OrderExternalSnapshot> safeList(List<OrderExternalSnapshot> snapshots) {
        if (Objects.isNull(snapshots)) {
            return List.of();
        }
        return snapshots;
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
        order.setStatus(orderStatusResolver.resolveStatus(snapshot));
    }

    private OrderExternalSnapshot recoverFinalSnapshot(ClientService clientService,
                                                       Instrument instrument,
                                                       Order order,
                                                       List<OrderExternalSnapshot> history,
                                                       List<OrderExternalSnapshot> archive) {
        OrderExternalSnapshot detail = tryGetOrderDetail(clientService, instrument, order);
        if (Objects.nonNull(detail)) {
            return detail;
        }

        OrderExternalSnapshot fromHistory = findByIdentity(history, order);
        if (Objects.nonNull(fromHistory)) {
            return fromHistory;
        }

        OrderExternalSnapshot fromArchive = findByIdentity(archive, order);
        if (Objects.nonNull(fromArchive)) {
            return fromArchive;
        }

        throw new IllegalStateException("Unable to recover final order snapshot for order: " + order.getInternalId());
    }

    private OrderExternalSnapshot tryGetOrderDetail(ClientService clientService, Instrument instrument, Order order) {
        OrderExternalSnapshot byExternalId = tryGetOrder(clientService,
                                                         instrument,
                                                         order.getExternalId(),
                                                         null);
        if (Objects.nonNull(byExternalId)) {
            return byExternalId;
        }
        return tryGetOrder(clientService, instrument, null, order.getInternalId());
    }

    private OrderExternalSnapshot tryGetOrder(ClientService clientService,
                                              Instrument instrument,
                                              String externalOrderId,
                                              String internalOrderId) {
        try {
            return clientService.getOrder(instrument.getExternalId(), externalOrderId, internalOrderId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private OrderExternalSnapshot findByIdentity(List<OrderExternalSnapshot> snapshots, Order order) {
        return snapshots.stream()
                        .filter(snapshot -> Objects.equals(snapshot.getExternalId(), order.getExternalId())
                                || Objects.equals(snapshot.getInternalId(), order.getInternalId()))
                        .findFirst()
                        .orElse(null);
    }
}
