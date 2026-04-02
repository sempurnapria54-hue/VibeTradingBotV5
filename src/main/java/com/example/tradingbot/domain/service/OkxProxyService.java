package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.model.okx.request.CancelAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.request.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.request.CreateAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.response.AlgoOrderResponse;
import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.client.service.okx.OkxRestClient;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.algo_order.AlgoOrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.tradingbot.util.Constant.Service.DEFAULT_TRADE_MODE;

@Service
@RequiredArgsConstructor
public class OkxProxyService {

    private final OkxRestClient okxRestClient;

    public List<OrderResponse> createOrder(OrderEntity orderEntity, InstrumentEntity instrumentEntity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setInstrumentId(instrumentEntity.getExternalId());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(orderEntity.getSide());
        request.setOrderType(orderEntity.getType());
        request.setSize(orderEntity.getSize());
        request.setPrice(orderEntity.getPrice());
        request.setClientOrderId(orderEntity.getInternalId());
        return okxRestClient.createOrder(request)
                            .getData();
    }

    public List<OrderResponse> cancelOrder(OrderEntity orderEntity, InstrumentEntity instrumentEntity) {
        CancelOrderRequest request = new CancelOrderRequest();
        request.setInstrumentId(instrumentEntity.getExternalId());
        request.setOrderId(orderEntity.getExternalId());
        request.setClientOrderId(orderEntity.getInternalId());
        return okxRestClient.cancelOrder(request)
                            .getData();
    }

    public List<AlgoOrderResponse> createAlgoOrder(AlgoOrderEntity algoOrderEntity, InstrumentEntity instrumentEntity) {
        var request = new CreateAlgoOrderRequest();
        request.setInstrumentId(instrumentEntity.getExternalId());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(request.getSide());
        request.setOrderType(algoOrderEntity.getType());
        request.setSize(algoOrderEntity.getSize());
        request.setTriggerPrice(algoOrderEntity.getTriggerPrice());
        request.setOrderPrice(algoOrderEntity.getTriggerExecutionPrice());
        request.setClientOrderId(algoOrderEntity.getInternalId());
        return okxRestClient.createAlgoOrder(request)
                            .getData();
    }

    public List<AlgoOrderResponse> cancelAlgoOrder(AlgoOrderEntity algoOrderEntity, InstrumentEntity instrumentEntity) {
        CancelAlgoOrderRequest request = new CancelAlgoOrderRequest();
        request.setInstrumentId(instrumentEntity.getExternalId());
        request.setAlgoOrderId(algoOrderEntity.getExternalId());
        request.setClientOrderId(algoOrderEntity.getInternalId());
        return okxRestClient.cancelAlgoOrder(request)
                            .getData();
    }

    public List<PositionResponse> closePosition(ClosePositionRequest request) {
        return okxRestClient.closePosition(request)
                            .getData();
    }
}
