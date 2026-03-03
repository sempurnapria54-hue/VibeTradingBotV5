package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.rest.model.request.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.tradingbot.util.factory.OrderFactory.createOrderEntity;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final TradingGuardService tradingGuardService;
    private final OrderDataService orderDataService;
    private final OkxProxyService okxProxyService;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;
    private final ClientManager clientManager;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderEntity createOrder(String exchangeInternalId, String instrumentInternalId, Order orderRequest) {
        ExchangeEntity exchangeEntity = exchangeService.getRequiredByInternalId(exchangeInternalId);
        InstrumentEntity instrumentEntity = instrumentService.getRequiredByExchangeIdAndInternalId(exchangeEntity.getId(), instrumentInternalId);
        tradingGuardService.assertTradingAllowed(exchangeEntity, instrumentEntity);

        OrderEntity orderEntity = new OrderEntity();
        orderMapper.domainToEntityOnCreate(orderRequest, orderEntity);
        orderDataService.save(orderEntity);

        clientManager.getClientService("OKX").createOrder(exchangeEntity, orderEntity);

        OrderResponse response = extractFirstOrder(okxProxyService.createOrder(orderEntity, instrumentEntity));
        orderEntity.applyOrderResponse(response);
        orderDataService.save(orderEntity);

        return orderEntity;
    }

    public OrderEntity cancelOrder(String exchangeInternalId, String instrumentInternalId, String orderId) {
        Long exchangeId = exchangeService.getRequiredByInternalId(exchangeInternalId).getId();
        InstrumentEntity instrumentEntity = instrumentService.getRequiredByExchangeIdAndInternalId(exchangeId, instrumentInternalId);
        OrderEntity orderEntity = orderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentEntity.getId(), orderId);
        OrderResponse response = extractFirstOrder(okxProxyService.cancelOrder(orderEntity, instrumentEntity));
        orderEntity.applyOrderResponse(response);
        return orderDataService.save(orderEntity);
    }

    private OrderResponse extractFirstOrder(List<OrderResponse> orders) {
        if (orders.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty order response");
        }
        return orders.getFirst();
    }

}
