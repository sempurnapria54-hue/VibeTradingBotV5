package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Прикладная логика {@link Exchange}: заведение биржи и чтение.
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeDataService exchangeDataService;

    public Exchange create(Exchange exchange) {
        exchange.setStatus(Exchange.Status.CREATED);
        return exchangeDataService.save(exchange);
    }

    public Exchange getRequiredById(Long id) {
        return exchangeDataService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + id));
    }
}
