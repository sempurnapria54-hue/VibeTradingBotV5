package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.persistence.repository.AlgoOrderRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link AlgoOrder}. condition и
 * linkedOrderExternalIds — JSONB через маппер.
 */
@Service
@RequiredArgsConstructor
public class AlgoOrderDataService {

    private final AlgoOrderRepository repository;
    private final AlgoOrderMapper mapper;

    @Transactional
    public AlgoOrder save(AlgoOrder algoOrder) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(algoOrder)));
    }

    @Transactional(readOnly = true)
    public AlgoOrder getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("AlgoOrder not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public AlgoOrder getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("AlgoOrder not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AlgoOrder> findByDealId(Long dealId) {
        return repository.findByDealId(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
