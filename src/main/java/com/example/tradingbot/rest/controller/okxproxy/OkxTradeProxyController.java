package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.*;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.rest.model.okxproxy.RestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/okx/v5/trade")
@RequiredArgsConstructor
public class OkxTradeProxyController {

    private final OkxTradeProxyService service;

    @GetMapping("/orders-pending")
    public RestResponse<OrderResponse> getOrdersPending(OrdersPendingRequest request) {
        return success(service.getOrdersPending(request));
    }

    @GetMapping("/order")
    public RestResponse<OrderResponse> getOrderDetails(OrderDetailsRequest request) {
        return success(service.getOrderDetails(request));
    }

    @GetMapping("/orders-history")
    public RestResponse<OrderResponse> getOrdersHistory(OrdersHistoryRequest request) {
        return success(service.getOrdersHistory(request));
    }

    @GetMapping("/orders-history-archive")
    public RestResponse<OrderResponse> getOrdersHistoryArchive(OrdersHistoryRequest request) {
        return success(service.getOrdersHistoryArchive(request));
    }

    @GetMapping("/fills")
    public RestResponse<TradeFillResponse> getFills(FillsRequest request) {
        return success(service.getFills(request));
    }

    @GetMapping("/fills-history")
    public RestResponse<TradeFillResponse> getFillsHistory(FillsRequest request) {
        return success(service.getFillsHistory(request));
    }

    @PostMapping("/fills-archive")
    public RestResponse<TradeFillsArchiveResponse> requestFillsArchive(@RequestBody FillsArchiveRequest request) {
        return success(service.requestFillsArchive(request));
    }

    @GetMapping("/fills-archive")
    public RestResponse<TradeFillsArchiveResponse> getFillsArchiveLink(FillsArchiveLinkRequest request) {
        return success(service.getFillsArchiveLink(request));
    }

    @PostMapping("/amend-order")
    public RestResponse<OrderResponse> amendOrder(@RequestBody AmendOrderRequest request) {
        return success(service.amendOrder(request));
    }

    @PostMapping("/close-position")
    public RestResponse<PositionResponse> closePosition(@RequestBody ClosePositionRequest request) {
        return success(service.closePosition(request));
    }

    private <T> RestResponse<T> success(List<T> data) {
        RestResponse<T> response = new RestResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
