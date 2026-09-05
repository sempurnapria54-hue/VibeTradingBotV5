package com.example.marketdata.persistence.service;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.marketdata.mapping.IndicatorValueMapper;
import com.example.marketdata.persistence.model.IndicatorValueEntity;
import com.example.marketdata.persistence.repository.IndicatorValueRepository;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для значений индикаторов. Запись
 * идемпотентна: значение с уже присутствующим временем свечи в паре
 * (инструмент, идентичность) повторно не вставляется.
 *
 * <p>Инструмент и идентичность уже проставлены на доменном значении —
 * их выставляет калькулятор, которому джоба их передала.
 */
@Service
@RequiredArgsConstructor
public class IndicatorDataService {

    private final IndicatorValueRepository repository;
    private final IndicatorValueMapper mapper;

    /**
     * Сохраняет только отсутствующие значения идентичности.
     *
     * @return число фактически вставленных значений.
     */
    @Transactional
    public Integer saveValues(Long instrumentId, Long indicatorConfigId, List<IndicatorValue> values) {
        if (isEmpty(values)) {
            return 0;
        }
        OffsetDateTime from = values.stream().map(IndicatorValue::getCandleTimestamp)
                .min(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime to = values.stream().map(IndicatorValue::getCandleTimestamp)
                .max(Comparator.naturalOrder()).orElseThrow();
        Set<OffsetDateTime> existing = new HashSet<>(
                repository.findCandleTimestampsInRange(instrumentId, indicatorConfigId, from, to));
        List<IndicatorValueEntity> toInsert = values.stream()
                .filter(value -> isFalse(existing.contains(value.getCandleTimestamp())))
                .map(mapper::domainToPersistence)
                .collect(toList());
        repository.saveAll(toInsert);
        return toInsert.size();
    }

    /** Последнее по времени свечи значение идентичности. */
    @Transactional(readOnly = true)
    public Optional<IndicatorValue> findLatest(Long instrumentId, Long indicatorConfigId) {
        return repository
                .findFirstByInstrumentIdAndIndicatorConfigIdOrderByCandleTimestampDesc(
                        instrumentId, indicatorConfigId)
                .map(mapper::persistenceToDomain);
    }

    /** Два последних значения идентичности — для slope и crossover. */
    @Transactional(readOnly = true)
    public List<IndicatorValue> findLatestTwo(Long instrumentId, Long indicatorConfigId) {
        return repository
                .findFirst2ByInstrumentIdAndIndicatorConfigIdOrderByCandleTimestampDesc(
                        instrumentId, indicatorConfigId)
                .stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }
}
