package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.okxproxy.*;
import com.example.tradingbot.domain.service.okxproxy.OkxTradeClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxTradeProxyService {

    private final OkxTradeClientService okxTradeClientService;

    public List<Order> getOrdersPending(OrdersPendingRequest request) { return okxTradeClientService.getOrdersPending(request); }
    public List<Order> getOrderDetails(OrderDetailsRequest request) { return okxTradeClientService.getOrderDetails(request); }
    public List<Order> getOrdersHistory(OrdersHistoryRequest request) { return okxTradeClientService.getOrdersHistory(request); }
    public List<Order> getOrdersHistoryArchive(OrdersHistoryRequest request) { return okxTradeClientService.getOrdersHistoryArchive(request); }
    public List<TradeFill> getFills(FillsRequest request) { return okxTradeClientService.getFills(request); }
    public List<TradeFill> getFillsHistory(FillsRequest request) { return okxTradeClientService.getFillsHistory(request); }
    public List<TradeFillsArchive> requestFillsArchive(FillsArchiveRequest request) { return okxTradeClientService.requestFillsArchive(request); }
    public List<TradeFillsArchive> getFillsArchiveLink(FillsArchiveLinkRequest request) { return okxTradeClientService.getFillsArchiveLink(request); }
    public List<Order> createOrder(CreateOrderRequest request) { return okxTradeClientService.createOrder(request); }
    public List<Order> amendOrder(AmendOrderRequest request) { return okxTradeClientService.amendOrder(request); }
    public List<Order> cancelOrder(CancelOrderRequest request) { return okxTradeClientService.cancelOrder(request); }
    public List<AlgoOrder> createAlgoOrder(CreateAlgoOrderRequest request) { return okxTradeClientService.createAlgoOrder(request); }
    public List<AlgoOrder> cancelAlgoOrder(CancelAlgoOrderRequest request) { return okxTradeClientService.cancelAlgoOrder(request); }
    public List<Position> closePosition(ClosePositionRequest request) { return okxTradeClientService.closePosition(request); }
}
