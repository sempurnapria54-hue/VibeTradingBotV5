package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    public final ExchangeDataService exchangeDataService;

    public ExchangeEntity getRequiredByName(String name) {
        return exchangeDataService.findRequiredByName(name);
    }

    public ExchangeEntity getRequiredByInternalId(String internalId) {
        return exchangeDataService.findRequiredByInternalId(internalId);
    }

    public ExchangeEntity createExchange(ExchangeEntity exchangeEntity) {
        exchangeEntity.initOnCreate();
        return exchangeDataService.save(exchangeEntity);
    }

    public List<ExchangeEntity> getAll() {
        return exchangeDataService.findAll();
    }
}
