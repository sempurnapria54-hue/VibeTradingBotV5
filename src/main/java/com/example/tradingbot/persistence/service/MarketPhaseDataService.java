package com.example.tradingbot.persistence.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.mapping.MarketPhaseMapper;
import com.example.tradingbot.persistence.repository.MarketPhaseRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для MarketPhase. Ключ — контейнер-
 * настройка (per-strategy). Запись идемпотентна по
 * (instrument, setting, candle_timestamp); при смене фазы пишется новый
 * актуальный результат поверх истории, а не правится старый.
 */
@Service
@RequiredArgsConstructor
public class MarketPhaseDataService {

    private final MarketPhaseRepository repository;
    private final MarketPhaseMapper mapper;

    /** Сохраняет фазу, если для (instrument, setting, candle_timestamp) её ещё нет. */
    @Transactional
    public void saveIfNew(MarketPhase phase) {
        Long settingId = phase.getSetting().getId();
        Boolean exists = repository.existsByInstrumentIdAndStrategyMarketPhaseSettingIdAndCandleTimestamp(
                phase.getInstrumentId(), settingId, phase.getCandleTimestamp());
        if (isTrue(exists)) {
            return;
        }
        repository.save(mapper.domainToPersistence(phase));
    }

    /** Производный checkpoint: «докуда посчитано» для (instrument, setting), или null. */
    @Transactional(readOnly = true)
    public OffsetDateTime findCheckpoint(Long instrumentId, Long strategyMarketPhaseSettingId) {
        return repository.findMaxCandleTimestamp(instrumentId, strategyMarketPhaseSettingId);
    }

    /** Актуальная (последняя по candle_timestamp) фаза для (instrument, setting). */
    @Transactional(readOnly = true)
    public Optional<MarketPhase> findLatest(Long instrumentId, Long strategyMarketPhaseSettingId) {
        return repository.findFirstByInstrumentIdAndStrategyMarketPhaseSettingIdOrderByCandleTimestampDesc(
                        instrumentId, strategyMarketPhaseSettingId)
                .map(mapper::persistenceToDomain);
    }
}
