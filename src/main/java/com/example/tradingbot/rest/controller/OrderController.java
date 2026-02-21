package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.trading.AlgoOrderService;
import com.example.tradingbot.domain.service.trading.OrderService;
import com.example.tradingbot.mapping.okxproxy.AlgoOrderMapper;
import com.example.tradingbot.mapping.okxproxy.OrderMapper;
import com.example.tradingbot.rest.model.request.order.CreateAlgoOrderRequest;
import com.example.tradingbot.rest.model.request.order.CreateOrderRequest;
import com.example.tradingbot.rest.model.response.order.AlgoOrderResponse;
import com.example.tradingbot.rest.model.response.order.OrderResponse;
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
    public OrderResponse createOrder(@PathVariable(name = "exchangeId") String exchangeId,
                                          @PathVariable(name = "instrumentId") String instrumentId,
                                          @RequestBody CreateOrderRequest request) {
        var order = orderService.createOrder(exchangeId, instrumentId, request);
        return orderMapper.domainToRest(order);
    }

    @DeleteMapping("{orderId}/cancel")
    public OrderResponse cancelOrders(@PathVariable(name = "exchangeId") String exchangeId,
                                      @PathVariable(name = "instrumentId") String instrumentId,
                                      @PathVariable(name = "orderId") String orderId) {
        var order = orderService.cancelOrder(exchangeId, instrumentId, orderId);
        return orderMapper.domainToRest(order);
    }

    @PostMapping("/algo")
    public AlgoOrderResponse createAlgoOrder(@PathVariable(name = "exchangeId") String exchangeId,
                                             @PathVariable(name = "instrumentId") String instrumentId,
                                             @RequestBody CreateAlgoOrderRequest request) {
        var algoOrder = algoOrderService.createAlgoOrder(exchangeId, instrumentId, request);
        return algoOrderMapper.domainToRest(algoOrder);
    }

    @DeleteMapping("/algo/{orderId}/cancel")
    public AlgoOrderResponse cancelAlgoOrder(@PathVariable(name = "exchangeId") String exchangeId,
                                             @PathVariable(name = "instrumentId") String instrumentId,
                                             @PathVariable(name = "orderId") String orderId) {
        var algoOrder = algoOrderService.cancelAlgoOrder(exchangeId, instrumentId, orderId);
        return algoOrderMapper.domainToRest(algoOrder);
    }
}
