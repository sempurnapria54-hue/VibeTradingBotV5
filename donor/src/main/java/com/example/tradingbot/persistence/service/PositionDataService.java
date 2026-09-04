package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.repository.PositionRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Position}. Строка адресуется
 * сделкой и парой «биржевой идентификатор, биржевое время создания»:
 * одного идентификатора мало — источник переиспользует его у
 * переоткрытой позиции (docs/models/domain/core/Position.md).
 * Собственного internalId у эпизода нет.
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

    /** Эпизоды сделки — закрытые и живой, в порядке возникновения. */
    @Transactional(readOnly = true)
    public List<Position> findEpisodes(Long dealId) {
        return repository.findByDealIdOrderByExternalCreatedAtAsc(dealId).stream()
                .map(mapper::persistenceToDomain)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Position getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + id));
    }
}
