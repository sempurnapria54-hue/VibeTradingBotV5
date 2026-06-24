package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.command.DealFinalizationState;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.mapping.DealFinalizationStateMapper;
import com.example.tradingbot.persistence.repository.DealFinalizationStateRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link DealFinalizationState}.
 * Адресуется по ключу идемпотентности (deal_id, type) и по сделке;
 * lastError — JSONB через маппер.
 */
@Service
@RequiredArgsConstructor
public class DealFinalizationStateDataService {

    private final DealFinalizationStateRepository repository;
    private final DealFinalizationStateMapper mapper;

    @Transactional
    public DealFinalizationState save(DealFinalizationState state) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(state)));
    }

    @Transactional(readOnly = true)
    public Optional<DealFinalizationState> findById(Long id) {
        return repository.findById(id).map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public Optional<DealFinalizationState> findByDealIdAndType(Long dealId, DealFinalizationType type) {
        return repository.findByDealIdAndType(dealId, type.name())
                .map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public List<DealFinalizationState> findByDealId(Long dealId) {
        return repository.findByDealId(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
