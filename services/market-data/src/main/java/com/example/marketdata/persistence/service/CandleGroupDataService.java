package com.example.marketdata.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.marketdata.mapping.CandleGroupMapper;
import com.example.marketdata.persistence.repository.CandleGroupRepository;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для единиц сбора свечей. Доменные перечни
 * (таймфрейм, статус) конвертируются в строки на границе репозитория.
 */
@Service
@RequiredArgsConstructor
public class CandleGroupDataService {

    private final CandleGroupRepository repository;
    private final CandleGroupMapper mapper;

    @Transactional
    public CandleGroup save(CandleGroup group) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(group)));
    }

    @Transactional(readOnly = true)
    public Optional<CandleGroup> findByInstrumentIdAndTimeframe(Long instrumentId, TimeFrame timeframe) {
        return repository.findByInstrumentIdAndTimeframe(instrumentId, timeframe.name())
                .map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByInstrumentId(Long instrumentId) {
        return repository.findByInstrumentId(instrumentId).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByStatusIn(Collection<CandleGroup.Status> statuses) {
        return repository.findByStatusIn(names(statuses)).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /**
     * Группы таймфрейма, готовые отдать историю расчёту. Популяция
     * производных: идентичность заказана глобально, а инструменты
     * приносят те группы, что уже собраны.
     */
    @Transactional(readOnly = true)
    public List<CandleGroup> findByTimeframeAndStatusIn(TimeFrame timeframe,
                                                        Collection<CandleGroup.Status> statuses) {
        return repository.findByTimeframeAndStatusIn(timeframe.name(), names(statuses)).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /** Инструменты, у которых хотя бы одна группа ещё не готова. */
    @Transactional(readOnly = true)
    public List<Long> findInstrumentIdsWithUnreadyGroups() {
        return repository.findInstrumentIdsWithUnreadyGroups(CandleGroup.Status.ACTIVE.name());
    }

    private List<String> names(Collection<CandleGroup.Status> statuses) {
        return statuses.stream().map(Enum::name).collect(toList());
    }
}
