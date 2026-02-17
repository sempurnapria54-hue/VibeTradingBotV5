package com.example.tradingbot.domain.service.admin;

import com.example.tradingbot.domain.model.admin.Exchange;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeAdminService {

    private final ExchangeDataService exchangeDataService;

    public Exchange createExchange(Exchange exchange) {
        ExchangeEntity exchangeEntity = new ExchangeEntity();
        exchangeEntity.setName(exchange.getName());
        exchangeEntity.setStatus(exchange.getStatus());
        exchangeEntity.setBaseUrl(exchange.getBaseUrl());

        ExchangeEntity savedExchange = exchangeDataService.create(exchangeEntity);
        return toDomain(savedExchange);
    }

    public List<Exchange> list() {
        return exchangeDataService.findAll()
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private Exchange toDomain(ExchangeEntity exchangeEntity) {
        return new Exchange(
            exchangeEntity.getId(),
            exchangeEntity.getName(),
            exchangeEntity.getStatus(),
            exchangeEntity.getBaseUrl()
        );
    }
}
