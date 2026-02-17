package com.example.tradingbot.domain.service.trading;

import com.example.tradingbot.domain.model.okxproxy.CancelOrderRequest;
import com.example.tradingbot.domain.model.okxproxy.CreateOrderRequest;
import com.example.tradingbot.domain.model.okxproxy.Order;
import com.example.tradingbot.domain.model.trading.CancelOrderCommand;
import com.example.tradingbot.domain.model.trading.CreateOrderCommand;
import com.example.tradingbot.domain.model.trading.OrderCommandResult;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private static final String ORDER_STATUS_CREATED = "CREATED";
    private static final String ORDER_STATUS_UPDATED = "UPDATED";
    private static final String DEFAULT_TRADE_MODE = "cross";

    private final TradingGuardService tradingGuardService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;
    private final OrderDataService orderDataService;
    private final OkxTradeProxyService okxTradeProxyService;

    @Transactional
    public OrderCommandResult createOrder(CreateOrderCommand command) {
        tradingGuardService.assertTradingAllowed(command.getExchangeId(), command.getInstrumentId());

        ExchangeEntity exchange = exchangeDataService.findById(command.getExchangeId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));
        InstrumentEntity instrument = instrumentDataService.findById(command.getInstrumentId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", "Instrument not found"));

        String internalId = UUID.randomUUID().toString();
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setExchange(exchange);
        orderEntity.setInstrument(instrument);
        orderEntity.setClientOrderId(internalId);
        orderEntity.setStatus(ORDER_STATUS_CREATED);
        orderEntity.setSide(command.getSide());
        orderEntity.setOrdType(command.getOrdType());
        orderEntity.setSz(command.getSz());
        orderEntity.setPx(command.getPx());
        orderDataService.save(orderEntity);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setInstrumentId(instrument.getInstId());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(command.getSide());
        request.setOrderType(command.getOrdType());
        request.setSize(command.getSz());
        request.setPrice(command.getPx());
        request.setClientOrderId(internalId);

        Order responseOrder = extractFirstOrder(okxTradeProxyService.createOrder(request));
        applyOrderResponse(orderEntity, responseOrder);
        orderDataService.save(orderEntity);

        return new OrderCommandResult(orderEntity.getClientOrderId(), orderEntity.getExchangeOrderId(), orderEntity.getState());
    }

    @Transactional
    public OrderCommandResult cancelOrder(CancelOrderCommand command) {
        tradingGuardService.assertTradingAllowed(command.getExchangeId(), command.getInstrumentId());

        InstrumentEntity instrument = instrumentDataService.findById(command.getInstrumentId())
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", "Instrument not found"));

        OrderEntity orderEntity = orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(
                command.getExchangeId(),
                command.getInstrumentId(),
                command.getInternalId()
            )
            .orElseThrow(() -> new TradingCommandException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));

        CancelOrderRequest request = new CancelOrderRequest();
        request.setInstrumentId(instrument.getInstId());
        request.setOrderId(orderEntity.getExchangeOrderId());
        request.setClientOrderId(orderEntity.getClientOrderId());

        Order responseOrder = extractFirstOrder(okxTradeProxyService.cancelOrder(request));
        applyOrderResponse(orderEntity, responseOrder);
        orderDataService.save(orderEntity);

        return new OrderCommandResult(orderEntity.getClientOrderId(), orderEntity.getExchangeOrderId(), orderEntity.getState());
    }

    private Order extractFirstOrder(List<Order> orders) {
        if (orders.isEmpty()) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY, "OKX_EMPTY_RESPONSE", "OKX returned empty order response");
        }
        return orders.getFirst();
    }

    private void applyOrderResponse(OrderEntity orderEntity, Order responseOrder) {
        orderEntity.setExchangeOrderId(responseOrder.getOrderId());
        orderEntity.setState(responseOrder.getState());
        orderEntity.setStatus(ORDER_STATUS_UPDATED);
        orderEntity.setSide(responseOrder.getSide());
        orderEntity.setOrdType(responseOrder.getOrderType());
        orderEntity.setPx(responseOrder.getPrice());
        orderEntity.setSz(responseOrder.getSize());
        orderEntity.setFillSz(responseOrder.getAccumulatedFillSize());
        orderEntity.setAvgPx(responseOrder.getAveragePrice());
        orderEntity.setFee(responseOrder.getFee());
        orderEntity.setCTime(parseLongSafe(responseOrder.getCreateTime()));
        orderEntity.setUTime(parseLongSafe(responseOrder.getUpdateTime()));
    }

    private Long parseLongSafe(String source) {
        if (Objects.isNull(source) || source.isBlank()) {
            return null;
        }
        return Long.parseLong(source);
    }
}
