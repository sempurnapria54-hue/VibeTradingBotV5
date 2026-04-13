package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.search_params.OrderSearchParams;
import com.example.tradingbot.exception.TradingCommandException;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.example.tradingbot.domain.model.order.Order.Status.CLOSED;
import static com.example.tradingbot.domain.model.order.Order.Status.CREATED;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderDataService orderDataService;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;
    private final ClientManager clientManager;
    private final OrderMapper mapper;
    private final DealDataService dealDataService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;

//    @Transactional
//    public Order createOrder(String exchangeInternalId, String instrumentInternalId, Order orderRequest) {
//        ExchangeEntity exchangeEntity = exchangeService.getRequiredByInternalId(exchangeInternalId);
//        InstrumentEntity instrumentEntity = instrumentService.getRequiredByExchangeIdAndInternalId(
//                exchangeEntity.getId(), instrumentInternalId);
//        tradingGuardService.assertTradingAllowed(exchangeEntity, instrumentEntity);
//
//        OrderEntity orderEntity = new OrderEntity();
//        orderMapper.domainToEntityOnCreate(orderRequest, orderEntity);
//        orderDataService.save(orderEntity);
//
//        clientManager.getClientService("OKX")
//                     .createOrder(exchangeEntity, orderEntity);
//
//        OrderResponse response = extractFirstOrder(okxProxyService.createOrder(orderEntity, instrumentEntity));
//        orderEntity.applyOrderResponse(response);
//        orderDataService.save(orderEntity);
//
//        return orderEntity;
//    }

//    public OrderEntity cancelOrder(String exchangeInternalId, String instrumentInternalId, String orderId) {
//        Long exchangeId = exchangeService.getRequiredByInternalId(exchangeInternalId)
//                                         .getId();
//        InstrumentEntity instrumentEntity = instrumentService.getRequiredByExchangeIdAndInternalId(exchangeId,
//                                                                                                   instrumentInternalId);
//        OrderEntity orderEntity = orderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId,
//                                                                                                           instrumentEntity.getId(),
//                                                                                                           orderId);
//        OrderResponse response = extractFirstOrder(okxProxyService.cancelOrder(orderEntity, instrumentEntity));
//        orderEntity.applyOrderResponse(response);
//        return orderDataService.save(orderEntity);
//    }

    private OrderResponse extractFirstOrder(List<OrderResponse> orders) {
        if (orders.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE",
                                              "OKX returned empty order response");
        }
        return orders.getFirst();
    }

    public Order getByInternalId(String internalOrderId) {
        return orderDataService.findRequiredByInternalId(internalOrderId);
    }

    public Page<Order> getByParams(OrderSearchParams searchParams, Pageable pageable) {
        return orderDataService.search(searchParams, pageable);
    }

    @Transactional
    public Order createOrder(String dealInternalId, Order request) {
        Deal deal = dealDataService.findRequiredByInternalId(dealInternalId);

        Order order = new Order();
        mapper.domainToDomainOnCreate(request, order);
        order.setStatus(CREATED);
        order.setDealId(deal.getId());
        order.getAttachedAlgoOrders()
             .forEach(stopLoss -> stopLoss.setStatus(AttachedAlgoOrder.Status.CREATED));
        return orderDataService.save(order);
    }

    @Transactional
    public Order cancelOrder(String internalOrderId) {
        Order order = orderDataService.findRequiredByInternalId(internalOrderId);
        Instrument instrument = instrumentDataService.findRequiredByDealId(order.getDealId());
        Exchange exchange = exchangeDataService.findRequiredById(instrument.getExchangeId());
        ClientService clientService = clientManager.getClientService(exchange.getName());


        List<Order> orders = clientService.cancelOrder(order, instrument.getExternalId());


        OrderExternalSnapshot cancelledSnapshot = clientService.getOrder(instrument.getExternalId(),
                                                                         order.getExternalId(),
                                                                         order.getInternalId());


        checkOnCancel(cancelledSnapshot);
        mapper.updateDomainFromExternalSnapshot(cancelledSnapshot, order);
        order.setStatus(CLOSED);
        return orderDataService.save(order);
    }

    private Order findRequiredByExternalId(List<Order> orders, String externalId) {
        return orders.stream()
                     .filter(ord -> Objects.equals(externalId, ord.getExternalId()))
                     .findFirst()
                     .orElseThrow(() -> new TradingCommandException(HttpStatus.BAD_GATEWAY,
                                                                    "EMPTY_RESPONSE",
                                                                    "External order missing.")
                     );

    }

    private void checkOnCancel(OrderExternalSnapshot order) {

    }


    @Transactional
    public void refreshPendingOrders(Exchange exchange, Instrument instrument) {
        List<OrderExternalSnapshot> externalSnapshots = clientManager.getClientService(exchange.getName())
                                                                     .getActiveOrdersByInstrument(instrument);
        if (externalSnapshots == null) {
            externalSnapshots = List.of();
        }
        List<Order> localOrders = orderDataService.findByInstrumentId(instrument.getId());
        if (localOrders == null) {
            localOrders = List.of();
        }

        for (Order localOrder : localOrders) {
            if (localOrder == null) {
                continue;
            }
            OrderExternalSnapshot matching = findMatchingByExternalId(externalSnapshots, localOrder.getExternalId());
            if (matching == null) {
                localOrder.setStatus(CLOSED);
                orderDataService.save(localOrder);
                continue;
            }
            mapper.updateDomainFromExternalSnapshot(matching, localOrder);
            localOrder.setStatus(resolveOrderStatus(matching.getExternalStatus()));
            orderDataService.save(localOrder);
        }

        for (OrderExternalSnapshot snapshot : externalSnapshots) {
            if (snapshot == null || snapshot.getExternalId() == null) {
                continue;
            }
            if (existsByExternalId(localOrders, snapshot.getExternalId())) {
                continue;
            }
            Order created = resolveUnknownOrder(snapshot, instrument.getId());
            orderDataService.save(created);
        }
    }

    public void createEntryOrder(Deal deal) {

    }

    public void refreshEntryOrder(Deal deal) {

    }

    private boolean existsByExternalId(List<Order> orders, String externalId) {
        for (Order order : orders) {
            if (order == null) {
                continue;
            }
            if (Objects.equals(order.getExternalId(), externalId)) {
                return true;
            }
        }
        return false;
    }

    private Order resolveUnknownOrder(OrderExternalSnapshot snapshot, Long instrumentId) {
        Optional<Deal> dealOptional = dealDataService.findLatestByInstrumentId(instrumentId);
        if (dealOptional.isEmpty()) {
            throw new IllegalStateException("Deal is missing for instrument: " + instrumentId);
        }
        Order created = new Order();
        created.setDealId(dealOptional.get()
                                      .getId());
        created.setInternalId(resolveInternalId(snapshot));
        mapper.updateDomainFromExternalSnapshot(snapshot, created);
        created.setStatus(resolveOrderStatus(snapshot.getExternalStatus()));
        return created;
    }

    private OrderExternalSnapshot findMatchingByExternalId(List<OrderExternalSnapshot> snapshots, String externalId) {
        for (OrderExternalSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            if (Objects.equals(snapshot.getExternalId(), externalId)) {
                return snapshot;
            }
        }
        return null;
    }

    private String resolveInternalId(OrderExternalSnapshot snapshot) {
        if (snapshot.getInternalId() != null && !snapshot.getInternalId()
                                                         .isBlank()) {
            return snapshot.getInternalId();
        }
        return UUID.randomUUID()
                   .toString();
    }

    private Order.Status resolveOrderStatus(String externalStatus) {
        if (externalStatus == null) {
            return PENDING;
        }
        if ("live".equalsIgnoreCase(externalStatus)) {
            return PENDING;
        }
        if ("partially_filled".equalsIgnoreCase(externalStatus)) {
            return Order.Status.PARTIALLY_COMPLETED;
        }
        if ("filled".equalsIgnoreCase(externalStatus)) {
            return Order.Status.COMPLETED;
        }
        if ("canceled".equalsIgnoreCase(externalStatus)) {
            return CLOSED;
        }
        return PENDING;
    }
}
