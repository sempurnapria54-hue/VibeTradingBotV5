package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.dto.AmendOrderRequest;
import com.example.tradingbot.client.okx.dto.ClosePositionRequest;
import com.example.tradingbot.client.okx.dto.FillsArchiveLinkRequest;
import com.example.tradingbot.client.okx.dto.FillsArchiveRequest;
import com.example.tradingbot.client.okx.dto.FillsRequest;
import com.example.tradingbot.client.okx.dto.OrderDetailsRequest;
import com.example.tradingbot.client.okx.dto.OrderResponse;
import com.example.tradingbot.client.okx.dto.OrdersHistoryRequest;
import com.example.tradingbot.client.okx.dto.OrdersPendingRequest;
import com.example.tradingbot.client.okx.dto.PositionResponse;
import com.example.tradingbot.client.okx.dto.TradeFillResponse;
import com.example.tradingbot.client.okx.dto.TradeFillsArchiveResponse;
import com.example.tradingbot.domain.service.OkxTradeProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/okx/v5/trade")
@RequiredArgsConstructor
public class OkxTradeProxyController {

    private final OkxTradeProxyService service;

    @GetMapping("/orders-pending")
    public List<OrderResponse> getOrdersPending(OrdersPendingRequest request) {
        return service.getOrdersPending(request);
    }

    @GetMapping("/order")
    public List<OrderResponse> getOrderDetails(OrderDetailsRequest request) {
        return service.getOrderDetails(request);
    }

    @GetMapping("/orders-history")
    public List<OrderResponse> getOrdersHistory(OrdersHistoryRequest request) {
        return service.getOrdersHistory(request);
    }

    @GetMapping("/orders-history-archive")
    public List<OrderResponse> getOrdersHistoryArchive(OrdersHistoryRequest request) {
        return service.getOrdersHistoryArchive(request);
    }

    @GetMapping("/fills")
    public List<TradeFillResponse> getFills(FillsRequest request) {
        return service.getFills(request);
    }

    @GetMapping("/fills-history")
    public List<TradeFillResponse> getFillsHistory(FillsRequest request) {
        return service.getFillsHistory(request);
    }

    @PostMapping("/fills-archive")
    public List<TradeFillsArchiveResponse> requestFillsArchive(@RequestBody FillsArchiveRequest request) {
        return service.requestFillsArchive(request);
    }

    @GetMapping("/fills-archive")
    public List<TradeFillsArchiveResponse> getFillsArchiveLink(FillsArchiveLinkRequest request) {
        return service.getFillsArchiveLink(request);
    }

    @PostMapping("/amend-order")
    public List<OrderResponse> amendOrder(@RequestBody AmendOrderRequest request) {
        return service.amendOrder(request);
    }

    @PostMapping("/close-position")
    public List<PositionResponse> closePosition(@RequestBody ClosePositionRequest request) {
        return service.closePosition(request);
    }
}
