package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.mapping.DealTrancheMapper;
import com.example.tradingbot.persistence.repository.DealTrancheRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link DealTranche}. Выборка траншей
 * идёт по сделке и ограничена ею — число траншей одной сделки задаёт
 * стратегия, поэтому безлимитного чтения здесь нет по построению.
 */
@Service
@RequiredArgsConstructor
public class DealTrancheDataService {

    private final DealTrancheRepository repository;
    private final DealTrancheMapper mapper;

    @Transactional
    public DealTranche save(DealTranche tranche) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(tranche)));
    }

    @Transactional(readOnly = true)
    public List<DealTranche> findByDealId(Long dealId) {
        return repository.findByDealIdOrderByIdAsc(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DealTranche getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalStateException("Deal tranche not found: " + internalId));
    }
}
