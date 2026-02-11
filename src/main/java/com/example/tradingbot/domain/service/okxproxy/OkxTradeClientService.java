package com.example.tradingbot.domain.service.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.domain.model.okxproxy.*;
import com.example.tradingbot.mapping.okxproxy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxTradeClientService {

    private final OkxRestClient okxRestClient;
    private final OkxProxyRequestMapper requestMapper;
    private final OrderMapper orderMapper;
    private final TradeFillMapper tradeFillMapper;
    private final TradeFillsArchiveMapper tradeFillsArchiveMapper;
    private final AlgoOrderMapper algoOrderMapper;
    private final PositionMapper positionMapper;

    public List<Order> getOrdersPending(OrdersPendingRequest request) { return okxRestClient.getOrdersPending(requestMapper.domainToClient(request)).getData().stream().map(orderMapper::clientToDomain).toList(); }
    public List<Order> getOrderDetails(OrderDetailsRequest request) { return okxRestClient.getOrderDetails(requestMapper.domainToClient(request)).getData().stream().map(orderMapper::clientToDomain).toList(); }
    public List<AlgoOrder> getOrdersAlgoPending(OrdersAlgoPendingRequest request) { return okxRestClient.getOrdersAlgoPending(requestMapper.domainToClient(request)).getData().stream().map(algoOrderMapper::clientToDomain).toList(); }
    public List<Order> getOrdersHistory(OrdersHistoryRequest request) { return okxRestClient.getOrdersHistory(requestMapper.domainToClient(request)).getData().stream().map(orderMapper::clientToDomain).toList(); }
    public List<Order> getOrdersHistoryArchive(OrdersHistoryRequest request) { return okxRestClient.getOrdersHistoryArchive(requestMapper.domainToClient(request)).getData().stream().map(orderMapper::clientToDomain).toList(); }
    public List<TradeFill> getFills(FillsRequest request) { return okxRestClient.getFills(requestMapper.domainToClient(request)).getData().stream().map(tradeFillMapper::clientToDomain).toList(); }
    public List<TradeFill> getFillsHistory(FillsRequest request) { return okxRestClient.getFillsHistory(requestMapper.domainToClient(request)).getData().stream().map(tradeFillMapper::clientToDomain).toList(); }
    public List<TradeFillsArchive> requestFillsArchive(FillsArchiveRequest request) { return okxRestClient.requestFillsArchive(requestMapper.domainToClient(request)).getData().stream().map(tradeFillsArchiveMapper::clientToDomain).toList(); }
    public List<TradeFillsArchive> getFillsArchiveLink(FillsArchiveLinkRequest request) { return okxRestClient.getFillsArchiveLink(requestMapper.domainToClient(request)).getData().stream().map(tradeFillsArchiveMapper::clientToDomain).toList(); }
    public List<Order> createOrder(CreateOrderRequest request) { return okxRestClient.createOrder(requestMapper.domainToClient(request)).getData().stream().map(orderMapper::clientToDomain).toList(); }
    public List<Order> amendOrder(AmendOrderRequest request) { return okxRestClient.amendOrder(requestMapper.domainToClient(request)).getData().stream().map(orderMapper::clientToDomain).toList(); }
    public List<Order> cancelOrder(CancelOrderRequest request) { return okxRestClient.cancelOrder(requestMapper.domainToClient(request)).getData().stream().map(orderMapper::clientToDomain).toList(); }
    public List<AlgoOrder> createAlgoOrder(CreateAlgoOrderRequest request) { return okxRestClient.createAlgoOrder(requestMapper.domainToClient(request)).getData().stream().map(algoOrderMapper::clientToDomain).toList(); }
    public List<AlgoOrder> cancelAlgoOrder(CancelAlgoOrderRequest request) { return okxRestClient.cancelAlgoOrder(requestMapper.domainToClient(request)).getData().stream().map(algoOrderMapper::clientToDomain).toList(); }
    public List<Position> closePosition(ClosePositionRequest request) { return okxRestClient.closePosition(requestMapper.domainToClient(request)).getData().stream().map(positionMapper::clientToDomain).toList(); }
}
