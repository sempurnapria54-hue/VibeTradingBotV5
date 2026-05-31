package com.example.tradingbot.rest.controller;

import com.example.tradingbot.domain.service.core.ExchangeService;
import com.example.tradingbot.mapping.ExchangeMapper;
import com.example.tradingbot.rest.model.request.exchange.CreateExchangeRequest;
import com.example.tradingbot.rest.model.response.exchange.ExchangeContainerResponse;
import com.example.tradingbot.rest.model.response.exchange.ExchangeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exchanges")
public class ExchangeController {

    private final ExchangeService exchangeService;
    private final ExchangeMapper exchangeMapper;

    @PostMapping
    public ExchangeResponse createExchange(@RequestBody CreateExchangeRequest request) {
        var domainRq = exchangeMapper.restToDomain(request);
        var domainExchange = exchangeService.createExchange(domainRq);
        return exchangeMapper.domainToRest(domainExchange);
    }

    @GetMapping
    public ExchangeContainerResponse getAll() {
        var exchanges = exchangeService.getAll();
        return exchangeMapper.domainListToRestContainer(exchanges);
    }
}
