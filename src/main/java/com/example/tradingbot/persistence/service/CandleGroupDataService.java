package com.example.tradingbot.persistence.service;

import static java.util.stream.Collectors.toList;

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
 * {@code instrumentId} домена. Доменные enum'ы (таймфрейм, статус)
 * конвертируются в строки на границе репозитория.
 */
@Service
@RequiredArgsConstructor
public class CandleGroupDataService {

    private final CandleGroupRepository repository;
    private final InstrumentRepository instrumentRepository;
    private final CandleGroupMapper mapper;

    @Transactional
    public CandleGroup save(CandleGroup group) {
        CandleGroupEntity entity = mapper.domainToPersistence(group);
        entity.setInstrument(instrumentRepository.getReferenceById(group.getInstrumentId()));
        return mapper.persistenceToDomain(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByInstrumentId(Long instrumentId) {
        return repository.findByInstrument_Id(instrumentId).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    @Transactional(readOnly = true)
    public Optional<CandleGroup> findByInstrumentIdAndTimeframe(Long instrumentId, TimeFrame timeframe) {
        return repository.findByInstrument_IdAndTimeframe(instrumentId, timeframe.name())
                .map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByStatusIn(Collection<CandleGroup.Status> statuses) {
        List<String> statusNames = statuses.stream().map(Enum::name).collect(toList());
        return repository.findByStatusIn(statusNames).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }
}
