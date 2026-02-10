package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.domain.service.OkxTradeProxyService;
import com.example.tradingbot.mapping.okxproxy.*;
import com.example.tradingbot.rest.model.okxproxy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/okx/v5/trade")
@RequiredArgsConstructor
public class OkxTradeProxyController {

    private final OkxTradeProxyService service;
    private final OkxProxyRequestMapper requestMapper;
    private final OrderMapper orderMapper;
    private final TradeFillMapper tradeFillMapper;
    private final TradeFillsArchiveMapper archiveMapper;
    private final AlgoOrderMapper algoOrderMapper;
    private final PositionMapper positionMapper;

    @GetMapping("/orders-pending")
    public RestResponse<Order> getOrdersPending(OrdersPendingRequest request) { return success(service.getOrdersPending(requestMapper.restToDomain(request)).stream().map(orderMapper::domainToRest).toList()); }
    @GetMapping("/order")
    public RestResponse<Order> getOrderDetails(OrderDetailsRequest request) { return success(service.getOrderDetails(requestMapper.restToDomain(request)).stream().map(orderMapper::domainToRest).toList()); }
    @GetMapping("/orders-history")
    public RestResponse<Order> getOrdersHistory(OrdersHistoryRequest request) { return success(service.getOrdersHistory(requestMapper.restToDomain(request)).stream().map(orderMapper::domainToRest).toList()); }
    @GetMapping("/orders-history-archive")
    public RestResponse<Order> getOrdersHistoryArchive(OrdersHistoryRequest request) { return success(service.getOrdersHistoryArchive(requestMapper.restToDomain(request)).stream().map(orderMapper::domainToRest).toList()); }
    @GetMapping("/fills")
    public RestResponse<TradeFill> getFills(FillsRequest request) { return success(service.getFills(requestMapper.restToDomain(request)).stream().map(tradeFillMapper::domainToRest).toList()); }
    @GetMapping("/fills-history")
    public RestResponse<TradeFill> getFillsHistory(FillsRequest request) { return success(service.getFillsHistory(requestMapper.restToDomain(request)).stream().map(tradeFillMapper::domainToRest).toList()); }
    @PostMapping("/fills-archive")
    public RestResponse<TradeFillsArchive> requestFillsArchive(@RequestBody FillsArchiveRequest request) { return success(service.requestFillsArchive(requestMapper.restToDomain(request)).stream().map(archiveMapper::domainToRest).toList()); }
    @GetMapping("/fills-archive")
    public RestResponse<TradeFillsArchive> getFillsArchiveLink(FillsArchiveLinkRequest request) { return success(service.getFillsArchiveLink(requestMapper.restToDomain(request)).stream().map(archiveMapper::domainToRest).toList()); }
    @PostMapping("/order")
    public RestResponse<Order> createOrder(@RequestBody CreateOrderRequest request) { return success(service.createOrder(requestMapper.restToDomain(request)).stream().map(orderMapper::domainToRest).toList()); }
    @PostMapping("/amend-order")
    public RestResponse<Order> amendOrder(@RequestBody AmendOrderRequest request) { return success(service.amendOrder(requestMapper.restToDomain(request)).stream().map(orderMapper::domainToRest).toList()); }
    @PostMapping("/cancel-order")
    public RestResponse<Order> cancelOrder(@RequestBody CancelOrderRequest request) { return success(service.cancelOrder(requestMapper.restToDomain(request)).stream().map(orderMapper::domainToRest).toList()); }
    @PostMapping("/order-algo")
    public RestResponse<AlgoOrder> createAlgoOrder(@RequestBody CreateAlgoOrderRequest request) { return success(service.createAlgoOrder(requestMapper.restToDomain(request)).stream().map(algoOrderMapper::domainToRest).toList()); }
    @PostMapping("/cancel-algos")
    public RestResponse<AlgoOrder> cancelAlgoOrder(@RequestBody CancelAlgoOrderRequest request) { return success(service.cancelAlgoOrder(requestMapper.restToDomain(request)).stream().map(algoOrderMapper::domainToRest).toList()); }
    @PostMapping("/close-position")
    public RestResponse<Position> closePosition(@RequestBody ClosePositionRequest request) { return success(service.closePosition(requestMapper.restToDomain(request)).stream().map(positionMapper::domainToRest).toList()); }

    private <T> RestResponse<T> success(List<T> data) {
        RestResponse<T> response = new RestResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
