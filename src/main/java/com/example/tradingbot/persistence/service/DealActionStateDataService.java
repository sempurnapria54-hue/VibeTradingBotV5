package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.mapping.DealActionStateMapper;
import com.example.tradingbot.persistence.repository.DealActionStateRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link DealActionState}. Адресуется
 * по ключу идемпотентности (deal_id, strategy_action_id); target и
 * lastError — JSONB через маппер.
 */
@Service
@RequiredArgsConstructor
public class DealActionStateDataService {

    private final DealActionStateRepository repository;
    private final DealActionStateMapper mapper;

    @Transactional
    public DealActionState save(DealActionState state) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(state)));
    }

    @Transactional(readOnly = true)
    public Optional<DealActionState> findByDealIdAndStrategyActionId(Long dealId, Long strategyActionId) {
        return repository.findByDealIdAndStrategyActionId(dealId, strategyActionId)
                .map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public List<DealActionState> findByDealId(Long dealId) {
        return repository.findByDealId(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }
}
