package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.*;
import com.example.tradingbot.client.okx.OkxRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/okx/v5/trade")
@RequiredArgsConstructor
public class OkxTradeProxyController {

    private final OkxRestClient okxRestClient;

    @GetMapping("/orders-pending")
    public OkxApiResponse<OrderResponse> getOrdersPending(OrdersPendingRequest request) {
        return okxRestClient.getOrdersPending(request);
    }

    @GetMapping("/order")
    public OkxApiResponse<OrderResponse> getOrderDetails(OrderDetailsRequest request) {
        return okxRestClient.getOrderDetails(request);
    }

    @GetMapping("/orders-history")
    public OkxApiResponse<OrderResponse> getOrdersHistory(OrdersHistoryRequest request) {
        return okxRestClient.getOrdersHistory(request);
    }

    @GetMapping("/orders-history-archive")
    public OkxApiResponse<OrderResponse> getOrdersHistoryArchive(OrdersHistoryRequest request) {
        return okxRestClient.getOrdersHistoryArchive(request);
    }

    @GetMapping("/fills")
    public OkxApiResponse<TradeFillResponse> getFills(FillsRequest request) {
        return okxRestClient.getFills(request);
    }

    @GetMapping("/fills-history")
    public OkxApiResponse<TradeFillResponse> getFillsHistory(FillsRequest request) {
        return okxRestClient.getFillsHistory(request);
    }

    @PostMapping("/fills-archive")
    public OkxApiResponse<TradeFillsArchiveResponse> requestFillsArchive(@RequestBody FillsArchiveRequest request) {
        return okxRestClient.requestFillsArchive(request);
    }

    @GetMapping("/fills-archive")
    public OkxApiResponse<TradeFillsArchiveResponse> getFillsArchiveLink(FillsArchiveLinkRequest request) {
        return okxRestClient.getFillsArchiveLink(request);
    }

    @PostMapping("/amend-order")
    public OkxApiResponse<OrderResponse> amendOrder(@RequestBody AmendOrderRequest request) {
        return okxRestClient.amendOrder(request);
    }

    @PostMapping("/close-position")
    public OkxApiResponse<PositionResponse> closePosition(@RequestBody ClosePositionRequest request) {
        return okxRestClient.closePosition(request);
    }
}
