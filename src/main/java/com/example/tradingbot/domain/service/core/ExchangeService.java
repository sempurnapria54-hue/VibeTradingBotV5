package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Прикладная логика {@link Exchange}: заведение биржи и чтение.
 * Fetch-or-throw живёт в {@link ExchangeDataService} (codestyle:
 * getRequiredBy* — в DataService), сюда делегируется.
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeDataService exchangeDataService;

    public Exchange create(Exchange exchange) {
        exchange.setStatus(Exchange.Status.CREATED);
        return exchangeDataService.save(exchange);
    }

    public Exchange getRequiredByInternalId(String internalId) {
        return exchangeDataService.getRequiredByInternalId(internalId);
    }

    /** Резолв internalId биржи по id — проекция одного поля, не вся сущность. */
    public String getRequiredInternalIdById(Long id) {
        return exchangeDataService.getRequiredInternalIdById(id);
    }
}
