package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.repository.PositionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Position}. Позиция
 * адресуется по deal_id (≤1 на сделку); собственного internalId нет.
 */
@Service
@RequiredArgsConstructor
public class PositionDataService {

    private final PositionRepository repository;
    private final PositionMapper mapper;

    @Transactional
    public Position save(Position position) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(position)));
    }

    @Transactional(readOnly = true)
    public Optional<Position> findByDealId(Long dealId) {
        return repository.findByDealId(dealId).map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public Position getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + id));
    }
}
