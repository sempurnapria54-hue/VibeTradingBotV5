package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.client.okx.dto.OrderResponse;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.domain.model.trading.CreateOrderRequest;
import com.example.tradingbot.domain.model.trading.OrderCommandResult;
import com.example.tradingbot.domain.service.ExchangeService;
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_IN_PROGRESS;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final TradingGuardService tradingGuardService;
    private final OrderDataService orderDataService;
    private final OkxTradeProxyService okxTradeProxyService;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;

    @Transactional
    public OrderCommandResult createOrder(String exchangeName, String instrumentName, CreateOrderRequest request) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);
        tradingGuardService.assertTradingAllowed(exchange, instrument);

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.initOnCreate(instrument, request);
        orderDataService.save(orderEntity);

        OrderResponse response = extractFirstOrder(okxTradeProxyService.createOrder(orderEntity));
        applyResponse(orderEntity, response);
        orderDataService.save(orderEntity);

        return new OrderCommandResult(orderEntity.getClientOrderId(), orderEntity.getExchangeOrderId(), orderEntity.getState());
    }

    public OrderEntity cancelOrder(String exchangeName, String instrumentName, String orderId) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);
        OrderEntity orderEntity = orderDataService.findRequiredByExchangeIdAndInstrumentIdAndClientOrderId(exchange.getId(), instrument.getId(), orderId);
        OrderResponse response = extractFirstOrder(okxTradeProxyService.cancelOrder(orderEntity));
        applyResponse(orderEntity, response);
        return orderDataService.save(orderEntity);
    }

    private OrderResponse extractFirstOrder(List<OrderResponse> orders) {
        if (orders.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty order response");
        }
        return orders.getFirst();
    }

    private void applyResponse(OrderEntity entity, OrderResponse response) {
        entity.setExchangeOrderId(response.getOrdId());
        entity.setState(response.getState());
        entity.setStatus(ORDER_STATUS_IN_PROGRESS);
        entity.setSide(response.getSide());
        entity.setOrdType(response.getOrdType());
        entity.setPx(response.getPx());
        entity.setSz(response.getSz());
        entity.setFillSz(response.getAccFillSz());
        entity.setAvgPx(response.getAvgPx());
        entity.setFee(response.getFee());
        entity.setCTime(parseLongSafe(response.getCTime()));
        entity.setUTime(parseLongSafe(response.getUTime()));
    }

    private Long parseLongSafe(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return Long.parseLong(source);
    }
}
