package com.example.tradingbot.domain.service;

import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.rest.model.request.CreateExchangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeDataService exchangeDataService;

    public ExchangeEntity getRequiredByInternalId(String internalId) {
        return exchangeDataService.findRequiredByInternalId(internalId);
    }

    public ExchangeEntity createExchange(CreateExchangeRequest request) {
        checkExistence(request.getName());
        var exchangeEntity = new ExchangeEntity();
        exchangeEntity.initOnCreate(request);
        return exchangeDataService.save(exchangeEntity);
    }

    public List<ExchangeEntity> getAll() {
        return exchangeDataService.findAll();
    }

    private void checkExistence(String name) {
        exchangeDataService.checkNotExists(name);
    }
}
