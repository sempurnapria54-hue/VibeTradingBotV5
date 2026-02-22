package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.trading.AlgoOrderService;
import com.example.tradingbot.domain.service.trading.OrderService;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.rest.model.request.CreateAlgoOrderRequest;
import com.example.tradingbot.rest.model.request.CreateOrderRequest;
import com.example.tradingbot.rest.model.response.AlgoOrderResponse;
import com.example.tradingbot.rest.model.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{exchangeId}/instruments/{instrumentId}/orders")
public class OrderController {

    private final OrderService orderService;
    private final AlgoOrderService algoOrderService;
    private final OrderMapper orderMapper;
    private final AlgoOrderMapper algoOrderMapper;

    @PostMapping()
    public OrderResponse createOrder(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                     @PathVariable(name = "instrumentId") String instrumentInternalId,
                                     @RequestBody CreateOrderRequest request) {
        var order = orderService.createOrder(exchangeInternalId, instrumentInternalId, request);
        return orderMapper.domainToRest(order);
    }

    @DeleteMapping("{orderId}/cancel")
    public OrderResponse cancelOrders(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                      @PathVariable(name = "instrumentId") String instrumentInternalId,
                                      @PathVariable(name = "orderId") String orderId) {
        var order = orderService.cancelOrder(exchangeInternalId, instrumentInternalId, orderId);
        return orderMapper.domainToRest(order);
    }

    @PostMapping("/algo")
    public AlgoOrderResponse createAlgoOrder(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                             @PathVariable(name = "instrumentId") String instrumentInternalId,
                                             @RequestBody CreateAlgoOrderRequest request) {
        var algoOrder = algoOrderService.createAlgoOrder(exchangeInternalId, instrumentInternalId, request);
        return algoOrderMapper.domainToRest(algoOrder);
    }

    @DeleteMapping("/algo/{orderId}/cancel")
    public AlgoOrderResponse cancelAlgoOrder(@PathVariable(name = "exchangeId") String exchangeInternalId,
                                             @PathVariable(name = "instrumentId") String instrumentInternalId,
                                             @PathVariable(name = "orderId") String orderId) {
        var algoOrder = algoOrderService.cancelAlgoOrder(exchangeInternalId, instrumentInternalId, orderId);
        return algoOrderMapper.domainToRest(algoOrder);
    }
}
