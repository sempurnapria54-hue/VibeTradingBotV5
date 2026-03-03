package com.example.tradingbot.util.factory;

import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.rest.model.request.CreateExchangeRequest;
import lombok.experimental.UtilityClass;

import java.util.UUID;

import static com.example.tradingbot.util.Constant.Status.Exchange.EXCHANGE_STATUS_CREATED;

@UtilityClass
public class ExchangeFactory {

    public static ExchangeEntity createExchangeEntity(CreateExchangeRequest request) {
        ExchangeEntity exchangeEntity = new ExchangeEntity();
        exchangeEntity.setInternalId(UUID.randomUUID().toString());
        exchangeEntity.setName(request.getName());
        exchangeEntity.setBaseUrl(request.getBaseUrl());
        exchangeEntity.setStatus(EXCHANGE_STATUS_CREATED);
        return exchangeEntity;
    }
}
