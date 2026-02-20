package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.model.trading.OrderCommandResult;
import com.example.tradingbot.domain.service.trading.AlgoOrderService;
import com.example.tradingbot.domain.service.trading.OrderService;
import com.example.tradingbot.mapping.okxproxy.AlgoOrderMapper;
import com.example.tradingbot.mapping.okxproxy.OrderMapper;
import com.example.tradingbot.rest.model.request.order.CreateAlgoOrderRequest;
import com.example.tradingbot.rest.model.request.order.CreateOrderRequest;
import com.example.tradingbot.rest.model.response.AlgoOrder;
import com.example.tradingbot.rest.model.response.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{exchangeName}/{instrumentName}/orders")
public class OrderController {

    private final OrderService orderService;
    private final AlgoOrderService algoOrderService;
    private final OrderMapper orderMapper;
    private final AlgoOrderMapper algoOrderMapper;

    @PostMapping()
    public OrderCommandResult createOrder(@PathVariable(name = "exchangeName") String exchangeName,
                                          @PathVariable(name = "instrumentName") String instrumentName,
                                          @RequestBody CreateOrderRequest request) {
        var domainRq = orderMapper.restToDomain(request);
        return orderService.createOrder(exchangeName, instrumentName, domainRq);
    }

    @DeleteMapping("{orderId}/cancel")
    public Order cancelOrders(@PathVariable(name = "exchangeName") String exchangeName,
                              @PathVariable(name = "instrumentName") String instrumentName,
                              @PathVariable(name = "orderId") String orderId) {
        var order = orderService.cancelOrder(exchangeName, instrumentName, orderId);
        return orderMapper.domainToRest(order);
    }

    @PostMapping("/algo")
    public AlgoOrder createAlgoOrder(@PathVariable(name = "exchangeName") String exchangeName,
                                     @PathVariable(name = "instrumentName") String instrumentName,
                                     @RequestBody CreateAlgoOrderRequest request) {
        var domainRq = algoOrderMapper.restToDomain(request);
        var algoOrder = algoOrderService.createAlgoOrder(exchangeName, instrumentName, domainRq);
        return algoOrderMapper.domainToRest(algoOrder);
    }

    @DeleteMapping("/algo/{orderId}/cancel")
    public AlgoOrder cancelAlgoOrder(@PathVariable(name = "exchangeName") String exchangeName,
                                     @PathVariable(name = "instrumentName") String instrumentName,
                                     @PathVariable(name = "orderId") String orderId) {
        var algoOrder = algoOrderService.cancelAlgoOrder(exchangeName, instrumentName, orderId);
        return algoOrderMapper.domainToRest(algoOrder);
    }
}
