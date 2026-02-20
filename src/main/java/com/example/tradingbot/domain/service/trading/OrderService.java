package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.model.okx.OrderResponse;
import com.example.tradingbot.rest.model.request.order.CreateOrderRequest;
import com.example.tradingbot.rest.model.response.order.OrderCommandResponse;
import com.example.tradingbot.domain.service.ExchangeService;
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final TradingGuardService tradingGuardService;
    private final OrderDataService orderDataService;
    private final OkxTradeProxyService okxTradeProxyService;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;

    @Transactional
    public OrderCommandResponse createOrder(String exchangeName, String instrumentName, CreateOrderRequest request) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);
        tradingGuardService.assertTradingAllowed(exchange, instrument);

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.initOnCreate(instrument, request);
        orderDataService.save(orderEntity);

        OrderResponse response = extractFirstOrder(okxTradeProxyService.createOrder(orderEntity));
        orderEntity.applyOrderResponse(response);
        orderDataService.save(orderEntity);

        return new OrderCommandResponse(orderEntity.getClientOrderId(), orderEntity.getExchangeOrderId(), orderEntity.getState());
    }

    public OrderEntity cancelOrder(String exchangeName, String instrumentName, String orderId) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);
        OrderEntity orderEntity = orderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientOrderId(exchange.getId(), instrument.getId(), orderId);
        OrderResponse response = extractFirstOrder(okxTradeProxyService.cancelOrder(orderEntity));
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
