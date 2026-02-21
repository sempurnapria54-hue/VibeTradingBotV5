package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.model.okx.OrderResponse;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.domain.service.ExchangeService;
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.rest.model.request.CreateOrderRequest;
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
    public OrderEntity createOrder(String exchangeInternalId, String instrumentInternalId, CreateOrderRequest request) {
        ExchangeEntity exchange = exchangeService.getRequiredByInternalId(exchangeInternalId);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndInternalId(exchange.getId(), instrumentInternalId);
        tradingGuardService.assertTradingAllowed(exchange, instrument);

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.initOnCreate(instrument, request);
        orderDataService.save(orderEntity);

        OrderResponse response = extractFirstOrder(okxTradeProxyService.createOrder(orderEntity));
        orderEntity.applyOrderResponse(response);
        orderDataService.save(orderEntity);

        return orderEntity;
    }

    public OrderEntity cancelOrder(String exchangeInternalId, String instrumentInternalId, String orderId) {
        Long exchangeId = exchangeService.getRequiredByInternalId(exchangeInternalId).getId();
        Long instrumentId = instrumentService.getRequiredIdByExchangeInternalIdAndInstrumentInternalId(exchangeInternalId, instrumentInternalId);
        OrderEntity orderEntity = orderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, orderId);
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
