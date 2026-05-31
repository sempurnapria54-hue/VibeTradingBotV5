package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.mapping.ExchangeMapper;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Exchange}.
 */
@Service
@RequiredArgsConstructor
public class ExchangeDataService {

    private final ExchangeRepository repository;
    private final ExchangeMapper mapper;

    @Transactional
    public Exchange save(Exchange exchange) {
        return mapper.entityToDomain(repository.save(mapper.domainToEntity(exchange)));
    }

    @Transactional(readOnly = true)
    public Optional<Exchange> findById(Long id) {
        return repository.findById(id).map(mapper::entityToDomain);
    }

    @Transactional(readOnly = true)
    public Optional<Exchange> findByName(String name) {
        return repository.findByName(name).map(mapper::entityToDomain);
    }
}
