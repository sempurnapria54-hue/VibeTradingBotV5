package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.mapping.DealMapper;
import com.example.tradingbot.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Deal}. Runtime graph
 * (orders/algoOrders/position) собирается отдельными DataService'ами по
 * deal_id, не здесь.
 */
@Service
@RequiredArgsConstructor
public class DealDataService {

    private final DealRepository repository;
    private final DealMapper mapper;

    @Transactional
    public Deal save(Deal deal) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(deal)));
    }

    @Transactional(readOnly = true)
    public Deal getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Deal not found: " + internalId));
    }
}
