package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.mapping.ExchangeMapper;
import com.example.tradingbot.persistence.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Exchange}. Здесь же —
 * fetch-or-throw чтения ({@code getRequiredBy*}).
 */
@Service
@RequiredArgsConstructor
public class ExchangeDataService {

    private final ExchangeRepository repository;
    private final ExchangeMapper mapper;

    @Transactional
    public Exchange save(Exchange exchange) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(exchange)));
    }

    @Transactional(readOnly = true)
    public Exchange getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + internalId));
    }

    /** Проекция: только internalId по id — без вытягивания всей сущности. */
    @Transactional(readOnly = true)
    public String getRequiredInternalIdById(Long id) {
        return repository.findInternalIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + id));
    }

    /** Проекция: только id по internalId — без вытягивания всей сущности. */
    @Transactional(readOnly = true)
    public Long getRequiredIdByInternalId(String internalId) {
        return repository.findIdByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found: " + internalId));
    }
}
