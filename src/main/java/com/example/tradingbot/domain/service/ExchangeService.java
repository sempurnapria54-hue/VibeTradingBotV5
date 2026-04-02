package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.exchange.Exchange.Status;
import com.example.tradingbot.mapping.ExchangeMapper;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeDataService exchangeDataService;
    private final ExchangeMapper exchangeMapper;

    public Exchange createExchange(Exchange request) {
        checkExistence(request.getName());
        Exchange exchange = new Exchange();
        exchangeMapper.domainToDomainOnCreate(request, exchange);
        exchange.setStatus(Status.CREATED);
        return exchangeDataService.save(exchange);
    }

    public Exchange getRequiredById(Long id) {
        return exchangeDataService.findRequiredById(id);
    }

    public List<Exchange> getAll() {
        return exchangeDataService.findAll();
    }

    private void checkExistence(String name) {
        exchangeDataService.checkNotExists(name);
    }
}
