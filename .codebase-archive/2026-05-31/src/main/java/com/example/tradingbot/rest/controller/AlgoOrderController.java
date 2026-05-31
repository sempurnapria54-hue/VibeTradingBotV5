package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.core.AlgoOrderService;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.rest.model.request.algo_order.CancelAlgoOrderRequest;
import com.example.tradingbot.rest.model.request.algo_order.CreateAlgoOrderRequest;
import com.example.tradingbot.rest.model.request.algo_order.SyncAlgoOrderRequest;
import com.example.tradingbot.rest.model.request.algo_order.search_params.AlgoOrderSearchParams;
import com.example.tradingbot.rest.model.response.algo_order.AlgoOrderPageResponse;
import com.example.tradingbot.rest.model.response.algo_order.AlgoOrderResponse;
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
@RequestMapping("/api/algo-orders")
public class AlgoOrderController {

    private final AlgoOrderService algoOrderService;
    private final AlgoOrderMapper algoOrderMapper;

    @GetMapping("/{algoOrderId}")
    public AlgoOrderResponse getById(@PathVariable(name = "algoOrderId") String algoOrderInternalId) {
        var result = algoOrderService.getByInternalId(algoOrderInternalId);
        return algoOrderMapper.domainToRest(result);
    }

    @GetMapping
    public AlgoOrderPageResponse getByParams(@ParameterObject AlgoOrderSearchParams request,
                                             @ParameterObject
                                             @PageableDefault(page = 0,
                                                     size = 20,
                                                     sort = "id",
                                                     direction = Sort.Direction.DESC)
                                             Pageable pageable) {
        var searchParams = algoOrderMapper.restToDomainSearchParams(request);
        var result = algoOrderService.getByParams(searchParams, pageable);
        return algoOrderMapper.domainToRest(result);
    }

    @PostMapping("/create")
    public AlgoOrderResponse createAlgoOrder(@RequestBody CreateAlgoOrderRequest request) {
        var domainRq = algoOrderMapper.restToDomain(request);
        var result = algoOrderService.createAlgoOrder(request.getDealInternalId(), domainRq);
        return algoOrderMapper.domainToRest(result);
    }

    @PutMapping("/{algoOrderId}/sync")
    public AlgoOrderResponse syncAlgoOrder(@RequestBody SyncAlgoOrderRequest request) {
        var result = algoOrderService.syncAlgoOrder(request.getExchangeInternalId(), request.getInternalId());
        return algoOrderMapper.domainToRest(result);
    }
}
