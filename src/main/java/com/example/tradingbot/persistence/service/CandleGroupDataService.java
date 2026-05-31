package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.mapping.CandleGroupMapper;
import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import com.example.tradingbot.persistence.repository.CandleGroupRepository;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link CandleGroup}. Связь группы с
 * инструментом (owning side агрегата) проставляется здесь по плоскому
 * {@code instrumentId} домена.
 */
@Service
@RequiredArgsConstructor
public class CandleGroupDataService {

    private final CandleGroupRepository repository;
    private final InstrumentRepository instrumentRepository;
    private final CandleGroupMapper mapper;

    @Transactional
    public CandleGroup save(CandleGroup group) {
        CandleGroupEntity entity = mapper.domainToEntity(group);
        entity.setInstrument(instrumentRepository.getReferenceById(group.getInstrumentId()));
        return mapper.entityToDomain(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<CandleGroup> findById(Long id) {
        return repository.findById(id).map(mapper::entityToDomain);
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByInstrumentId(Long instrumentId) {
        return repository.findByInstrument_Id(instrumentId).stream().map(mapper::entityToDomain).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CandleGroup> findByInstrumentIdAndTimeframe(Long instrumentId, TimeFrame timeframe) {
        return repository.findByInstrument_IdAndTimeframe(instrumentId, timeframe).map(mapper::entityToDomain);
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByStatusIn(Collection<CandleGroup.Status> statuses) {
        return repository.findByStatusIn(statuses).stream().map(mapper::entityToDomain).toList();
    }
}
