package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.ExchangeService;
import com.example.tradingbot.mapping.okxproxy.ExchangeMapper;
import com.example.tradingbot.rest.model.request.CreateExchangeRequest;
import com.example.tradingbot.rest.model.response.ExchangeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exchanges")
public class ExchangeController {

    private final ExchangeService exchangeService;
    private final ExchangeMapper exchangeMapper;

    @PostMapping
    public ExchangeResponse createExchange(@RequestBody CreateExchangeRequest request) {
        var domainExchange = exchangeService.createExchange(request);
        return exchangeMapper.domainToRest(domainExchange);
    }

    @GetMapping
    public List<ExchangeResponse> getAll() {
        var exchanges = exchangeService.getAll();
        return exchangeMapper.domainToRest(exchanges);
    }
}
