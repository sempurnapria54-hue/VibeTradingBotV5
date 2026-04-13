package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.core.OrderService;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.rest.model.request.algo_order.SyncAlgoOrderRequest;
import com.example.tradingbot.rest.model.request.order.CreateOrderRequest;
import com.example.tradingbot.rest.model.request.order.search_params.OrderSearchParams;
import com.example.tradingbot.rest.model.response.algo_order.AlgoOrderResponse;
import com.example.tradingbot.rest.model.response.order.OrderPageResponse;
import com.example.tradingbot.rest.model.response.order.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper mapper;

    @GetMapping("/{orderId}")
    public OrderResponse getById(@PathVariable(name = "orderId") String internalOrderId) {
        var result = orderService.getByInternalId(internalOrderId);
        return mapper.domainToRest(result);
    }

    @GetMapping
    public OrderPageResponse getByParams(@ParameterObject OrderSearchParams request,
                                         @ParameterObject
                                         @PageableDefault(page = 0,
                                                 size = 20,
                                                 sort = "id",
                                                 direction = Sort.Direction.DESC)
                                         Pageable pageable) {
        var searchParams = mapper.restToDomainSearchParams(request);
        var result = orderService.getByParams(searchParams, pageable);
        return mapper.domainToRest(result);
    }

    @PostMapping
    public OrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        var domainRq = mapper.restRequestToDomain(request);
        var order = orderService.createOrder(request.getDealInternalId(), domainRq);
        return mapper.domainToRest(order);
    }

    @PutMapping("/activate")
    public AlgoOrderResponse activateAlgoOrder(@RequestBody ActivateAlgoOrderRequest request) {
        var result = orderService.createOnExchange(request.getExchangeInternalId(),
                                                   request.getInstrumentInternalId(), request.getInternalId(),
                                                   request.getDealInternalId());
        return mapper.domainToRest(result);
    }

    @PutMapping("/sync/{algoOrderId}")
    public AlgoOrderResponse syncAlgoOrder(@RequestBody SyncAlgoOrderRequest request) {
        var result = orderService.syncAlgoOrder(request.getExchangeInternalId(), request.getInternalId());
        return mapper.domainToRest(result);
    }

    @DeleteMapping("{orderId}")
    public OrderResponse cancelOrder(@PathVariable(name = "orderId") String internalOrderId) {
        var order = orderService.cancelOrder(internalOrderId);
        return mapper.domainToRest(order);
    }

}