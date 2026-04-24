package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.core.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.search_params.OrderSearchParams;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshOrderExecutor;
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

import static com.example.tradingbot.domain.model.core.order.Order.Status.CLOSED;
import static com.example.tradingbot.domain.model.core.order.Order.Status.CREATED;

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
    private final RefreshOrderExecutor refreshOrderExecutor;

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
    public void refreshPendingOrders(Exchange exchange, Instrument instrument, Long dealId) {
        refreshOrderExecutor.execute(exchange, instrument, dealId);
    }

    public Order activateOrder(String exchangeInternalId, String internalOrderId) {
        throw new TradingCommandException(HttpStatus.NOT_IMPLEMENTED,
                                          "ORDER_ACTIVATION_NOT_IMPLEMENTED",
                                          "Order activation is not implemented in compile stabilization stage.");
    }

    public Order syncOrder(String exchangeInternalId, String internalOrderId) {
        throw new TradingCommandException(HttpStatus.NOT_IMPLEMENTED,
                                          "ORDER_SYNC_NOT_IMPLEMENTED",
                                          "Order sync is not implemented in compile stabilization stage.");
    }

}
