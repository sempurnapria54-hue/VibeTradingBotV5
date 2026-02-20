package com.example.tradingbot.domain.service;

import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    public final ExchangeDataService exchangeDataService;

    public ExchangeEntity getRequiredByName(String name) {
        return exchangeDataService.findRequiredByName(name);
    }

    public ExchangeEntity createExchange(ExchangeEntity exchangeEntity) {
        return exchangeDataService.save(exchangeEntity);
    }

    public List<ExchangeEntity> getAll() {
        return exchangeDataService.findAll();
    }
}
