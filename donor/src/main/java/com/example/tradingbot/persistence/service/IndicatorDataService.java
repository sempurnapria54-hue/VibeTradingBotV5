package com.example.tradingbot.persistence.service;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.mapping.IndicatorValueMapper;
import com.example.tradingbot.persistence.model.marketdata.IndicatorValueEntity;
import com.example.tradingbot.persistence.repository.IndicatorValueRepository;
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
 * Граница domain ↔ persistence для IndicatorValue. Запись идемпотентна:
 * значения с уже присутствующим candle_timestamp в (instrument, setting)
 * повторно не вставляются (UNIQUE(instrument_id,
 * strategy_indicator_setting_id, candle_timestamp)). Чтение — производный
 * checkpoint и последнее значение. instrumentId/strategyIndicatorSettingId
 * уже проставлены на доменном значении (их выставляет job/калькулятор) —
 * owner-ключевание (трек D).
 */
@Service
@RequiredArgsConstructor
public class IndicatorDataService {

    private final IndicatorValueRepository repository;
    private final IndicatorValueMapper mapper;

    /**
     * Сохраняет только отсутствующие значения настройки-владельца (дедуп
     * по candle_timestamp).
     *
     * @return число фактически вставленных значений.
     */
    @Transactional
    public Integer saveValues(Long instrumentId, Long strategyIndicatorSettingId, List<IndicatorValue> values) {
        if (isEmpty(values)) {
            return 0;
        }
        OffsetDateTime from = values.stream().map(IndicatorValue::getCandleTimestamp)
                .min(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime to = values.stream().map(IndicatorValue::getCandleTimestamp)
                .max(Comparator.naturalOrder()).orElseThrow();
        Set<OffsetDateTime> existing = new HashSet<>(
                repository.findCandleTimestampsInRange(instrumentId, strategyIndicatorSettingId, from, to));
        List<IndicatorValueEntity> toInsert = values.stream()
                .filter(value -> isFalse(existing.contains(value.getCandleTimestamp())))
                .map(mapper::domainToPersistence)
                .collect(toList());
        repository.saveAll(toInsert);
        return toInsert.size();
    }

    /** Последнее по candle_timestamp значение настройки (для раздачи потребителям). */
    @Transactional(readOnly = true)
    public Optional<IndicatorValue> findLatest(Long instrumentId, Long strategyIndicatorSettingId) {
        return repository
                .findFirstByInstrumentIdAndStrategyIndicatorSettingIdOrderByCandleTimestampDesc(
                        instrumentId, strategyIndicatorSettingId)
                .map(mapper::persistenceToDomain);
    }

    /** Два последних значения настройки (latest, previous) — для slope/crossover. */
    @Transactional(readOnly = true)
    public List<IndicatorValue> findLatestTwo(Long instrumentId, Long strategyIndicatorSettingId) {
        return repository
                .findFirst2ByInstrumentIdAndStrategyIndicatorSettingIdOrderByCandleTimestampDesc(
                        instrumentId, strategyIndicatorSettingId)
                .stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }
}
