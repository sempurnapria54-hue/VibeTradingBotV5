package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.okxproxy.*;
import com.example.tradingbot.domain.service.okxproxy.OkxTradeClientService;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.tradingbot.util.Constant.Service.DEFAULT_TRADE_MODE;

@Service
@RequiredArgsConstructor
public class OkxTradeProxyService {

    private final OkxTradeClientService okxTradeClientService;

    public List<Order> getOrdersPending(OrdersPendingRequest request) {
        return okxTradeClientService.getOrdersPending(request);
    }

    public List<Order> getOrderDetails(OrderDetailsRequest request) {
        return okxTradeClientService.getOrderDetails(request);
    }

    public List<Order> getOrdersHistory(OrdersHistoryRequest request) {
        return okxTradeClientService.getOrdersHistory(request);
    }

    public List<Order> getOrdersHistoryArchive(OrdersHistoryRequest request) {
        return okxTradeClientService.getOrdersHistoryArchive(request);
    }

    public List<TradeFill> getFills(FillsRequest request) {
        return okxTradeClientService.getFills(request);
    }

    public List<TradeFill> getFillsHistory(FillsRequest request) {
        return okxTradeClientService.getFillsHistory(request);
    }

    public List<TradeFillsArchive> requestFillsArchive(FillsArchiveRequest request) {
        return okxTradeClientService.requestFillsArchive(request);
    }

    public List<TradeFillsArchive> getFillsArchiveLink(FillsArchiveLinkRequest request) {
        return okxTradeClientService.getFillsArchiveLink(request);
    }

    public List<Order> createOrder(OrderEntity orderEntity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setInstrumentId(orderEntity.getInstrument().getExternalName());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(orderEntity.getSide());
        request.setOrderType(orderEntity.getType());
        request.setSize(orderEntity.getSz());
        request.setPrice(orderEntity.getPx());
        request.setClientOrderId(orderEntity.getClientOrderId());
        return okxTradeClientService.createOrder(request);
    }

    public List<Order> amendOrder(AmendOrderRequest request) {
        return okxTradeClientService.amendOrder(request);
    }

    public List<Order> cancelOrder(OrderEntity orderEntity) {
        CancelOrderRequest request = new CancelOrderRequest();
        request.setInstrumentId(orderEntity.getInstrument().getExternalName());
        request.setOrderId(orderEntity.getExchangeOrderId());
        request.setClientOrderId(orderEntity.getClientOrderId());
        return okxTradeClientService.cancelOrder(request);
    }

    public List<AlgoOrder> createAlgoOrder(AlgoOrderEntity algoOrderEntity) {
        var request = new CreateAlgoOrderRequest();
        request.setInstrumentId(algoOrderEntity.getInstrument().getExternalName());
        request.setTradeMode(DEFAULT_TRADE_MODE);
        request.setSide(request.getSide());
        request.setOrderType(algoOrderEntity.getAlgoType());
        request.setSize(algoOrderEntity.getSz());
        request.setTriggerPrice(algoOrderEntity.getTriggerPx());
        request.setOrderPrice(algoOrderEntity.getOrdPx());
        request.setClientOrderId(algoOrderEntity.getClientAlgoOrderId());
        return okxTradeClientService.createAlgoOrder(request);
    }

    public List<AlgoOrder> cancelAlgoOrder(AlgoOrderEntity algoOrderEntity) {
        CancelAlgoOrderRequest request = new CancelAlgoOrderRequest();
        request.setInstrumentId(algoOrderEntity.getInstrument().getExternalName());
        request.setAlgoOrderId(algoOrderEntity.getExchangeAlgoOrderId());
        request.setClientOrderId(algoOrderEntity.getClientAlgoOrderId());
        return okxTradeClientService.cancelAlgoOrder(request);
    }

    public List<Position> closePosition(ClosePositionRequest request) {
        return okxTradeClientService.closePosition(request);
    }
}
