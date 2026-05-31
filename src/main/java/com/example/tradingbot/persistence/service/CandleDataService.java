package com.example.tradingbot.persistence.service;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.persistence.model.candle.CandleEntity;
import com.example.tradingbot.persistence.repository.CandleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Candle}. Запись идемпотентна:
 * свечи с уже присутствующим {@code openTimestamp} в группе не
 * вставляются повторно (уникальность (candle_group_id, open_timestamp)).
 */
@Service
@RequiredArgsConstructor
public class CandleDataService {

    private final CandleRepository repository;
    private final CandleMapper mapper;

    /**
     * Сохраняет только новые (отсутствующие) свечи группы.
     *
     * @return число фактически вставленных свечей.
     */
    @Transactional
    public Integer saveCandles(Long candleGroupId, List<Candle> candles) {
        if (isEmpty(candles)) {
            return 0;
        }
        long from = candles.stream().mapToLong(Candle::getOpenTimestamp).min().orElseThrow();
        long to = candles.stream().mapToLong(Candle::getOpenTimestamp).max().orElseThrow();
        Set<Long> existing = new HashSet<>(
                repository.findOpenTimestampsInRange(candleGroupId, from, to));
        List<CandleEntity> toInsert = candles.stream()
                .filter(candle -> isFalse(existing.contains(candle.getOpenTimestamp())))
                .map(candle -> toEntity(candleGroupId, candle))
                .collect(toList());
        repository.saveAll(toInsert);
        return toInsert.size();
    }

    @Transactional(readOnly = true)
    public Long count(Long candleGroupId) {
        return repository.countByCandleGroupId(candleGroupId);
    }

    @Transactional(readOnly = true)
    public Long countInRange(Long candleGroupId, Long fromMillis, Long toMillis) {
        return repository.countInRange(candleGroupId, fromMillis, toMillis);
    }

    @Transactional(readOnly = true)
    public Long findMinOpenTimestamp(Long candleGroupId) {
        return repository.findMinOpenTimestamp(candleGroupId);
    }

    @Transactional(readOnly = true)
    public Long findMaxOpenTimestamp(Long candleGroupId) {
        return repository.findMaxOpenTimestamp(candleGroupId);
    }

    private CandleEntity toEntity(Long candleGroupId, Candle candle) {
        CandleEntity entity = mapper.domainToPersistence(candle);
        if (nonNull(entity)) {
            entity.setCandleGroupId(candleGroupId);
        }
        return entity;
    }
}
