package com.example.tradingbot.domain.service.okxproxy;

import com.example.tradingbot.client.model.okx.request.AmendOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.request.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.request.CreateAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.request.FillsArchiveLinkRequest;
import com.example.tradingbot.client.model.okx.request.FillsArchiveRequest;
import com.example.tradingbot.client.model.okx.request.FillsRequest;
import com.example.tradingbot.client.model.okx.request.OrderDetailsRequest;
import com.example.tradingbot.client.model.okx.request.OrdersAlgoPendingRequest;
import com.example.tradingbot.client.model.okx.request.OrdersHistoryRequest;
import com.example.tradingbot.client.model.okx.request.OrdersPendingRequest;
import com.example.tradingbot.client.service.okx.OkxRestClient;
import com.example.tradingbot.domain.model.*;
import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.mapping.TradeFillMapper;
import com.example.tradingbot.mapping.TradeFillsArchiveMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OkxTradeClientService {

    private final OkxRestClient okxRestClient;
    private final OrderMapper orderMapper;
    private final TradeFillMapper tradeFillMapper;
    private final TradeFillsArchiveMapper tradeFillsArchiveMapper;
    private final AlgoOrderMapper algoOrderMapper;
    private final PositionMapper positionMapper;

    public List<Order> getOrdersPending(OrdersPendingRequest request) {
        return okxRestClient.getOrdersPending(request).getData().stream().map(orderMapper::clientToDomain).toList();
    }

    public List<Order> getOrderDetails(OrderDetailsRequest request) {
        return okxRestClient.getOrderDetails(request).getData().stream().map(orderMapper::clientToDomain).toList();
    }

    public List<AlgoOrder> getOrdersAlgoPending(OrdersAlgoPendingRequest request) {
        return okxRestClient.getOrdersAlgoPending(request).getData().stream().map(algoOrderMapper::clientToDomain).toList();
    }

    public List<Order> getOrdersHistory(OrdersHistoryRequest request) {
        return okxRestClient.getOrdersHistory(request).getData().stream().map(orderMapper::clientToDomain).toList();
    }

    public List<Order> getOrdersHistoryArchive(OrdersHistoryRequest request) {
        return okxRestClient.getOrdersHistoryArchive(request).getData().stream().map(orderMapper::clientToDomain).toList();
    }

    public List<TradeFill> getFills(FillsRequest request) {
        return okxRestClient.getFills(request).getData().stream().map(tradeFillMapper::clientToDomain).toList();
    }

    public List<TradeFill> getFillsHistory(FillsRequest request) {
        return okxRestClient.getFillsHistory(request).getData().stream().map(tradeFillMapper::clientToDomain).toList();
    }

    public List<TradeFillsArchive> requestFillsArchive(FillsArchiveRequest request) {
        return okxRestClient.requestFillsArchive(request).getData().stream().map(tradeFillsArchiveMapper::clientToDomain).toList();
    }

    public List<TradeFillsArchive> getFillsArchiveLink(FillsArchiveLinkRequest request) {
        return okxRestClient.getFillsArchiveLink(request).getData().stream().map(tradeFillsArchiveMapper::clientToDomain).toList();
    }

    public List<Order> createOrder(CreateOrderRequest request) {
        return okxRestClient.createOrder(request).getData().stream().map(orderMapper::clientToDomain).toList();
    }

    public List<Order> amendOrder(AmendOrderRequest request) {
        return okxRestClient.amendOrder(request).getData().stream().map(orderMapper::clientToDomain).toList();
    }

    public List<Order> cancelOrder(CancelOrderRequest request) {
        return okxRestClient.cancelOrder(request).getData().stream().map(orderMapper::clientToDomain).toList();
    }

    public List<AlgoOrder> createAlgoOrder(CreateAlgoOrderRequest request) {
        return okxRestClient.createAlgoOrder(request).getData().stream().map(algoOrderMapper::clientToDomain).toList();
    }

    public List<AlgoOrder> cancelAlgoOrder(CancelAlgoOrderRequest request) {
        return okxRestClient.cancelAlgoOrder(request).getData().stream().map(algoOrderMapper::clientToDomain).toList();
    }

    public List<Position> closePosition(ClosePositionRequest request) {
        return okxRestClient.closePosition(request).getData().stream().map(positionMapper::clientToDomain).toList();
    }
}
