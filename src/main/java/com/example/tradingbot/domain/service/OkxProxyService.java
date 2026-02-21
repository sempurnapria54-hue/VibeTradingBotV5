package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.model.okx.AlgoOrderResponse;
import com.example.tradingbot.client.model.okx.CancelAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.CreateAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.OrderResponse;
import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.tradingbot.util.Constant.Service.DEFAULT_TRADE_MODE;

@Service
@RequiredArgsConstructor
public class OkxProxyService {

    private final OkxRestClient okxRestClient;

    public List<OrderResponse> createOrder(OrderEntity orderEntity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setInstrumentId(orderEntity.getInstrument().getExternalId());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(orderEntity.getSide());
        request.setOrderType(orderEntity.getType());
        request.setSize(orderEntity.getSize());
        request.setPrice(orderEntity.getPrice());
        request.setClientOrderId(orderEntity.getInternalId());
        return okxRestClient.createOrder(request).getData();
    }

    public List<OrderResponse> cancelOrder(OrderEntity orderEntity) {
        CancelOrderRequest request = new CancelOrderRequest();
        request.setInstrumentId(orderEntity.getInstrument().getExternalId());
        request.setOrderId(orderEntity.getExternalId());
        request.setClientOrderId(orderEntity.getInternalId());
        return okxRestClient.cancelOrder(request).getData();
    }

    public List<AlgoOrderResponse> createAlgoOrder(AlgoOrderEntity algoOrderEntity) {
        var request = new CreateAlgoOrderRequest();
        request.setInstrumentId(algoOrderEntity.getInstrument().getExternalId());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(request.getSide());
        request.setOrderType(algoOrderEntity.getType());
        request.setSize(algoOrderEntity.getSize());
        request.setTriggerPrice(algoOrderEntity.getTriggerPrice());
        request.setOrderPrice(algoOrderEntity.getOrderPrice());
        request.setClientOrderId(algoOrderEntity.getInternalOrderId());
        return okxRestClient.createAlgoOrder(request).getData();
    }

    public List<AlgoOrderResponse> cancelAlgoOrder(AlgoOrderEntity algoOrderEntity) {
        CancelAlgoOrderRequest request = new CancelAlgoOrderRequest();
        request.setInstrumentId(algoOrderEntity.getInstrument().getExternalId());
        request.setAlgoOrderId(algoOrderEntity.getExternalId());
        request.setClientOrderId(algoOrderEntity.getInternalOrderId());
        return okxRestClient.cancelAlgoOrder(request).getData();
    }

    public List<PositionResponse> closePosition(ClosePositionRequest request) {
        return okxRestClient.closePosition(request).getData();
    }
}
