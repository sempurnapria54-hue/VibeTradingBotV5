package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.CandleEntity;
import com.example.tradingbot.persistence.repository.CandleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandleDataService {

    private final CandleRepository candleRepository;

    @Transactional
    public CandleEntity save(CandleEntity candleEntity) {
        return candleRepository.save(candleEntity);
    }

    @Transactional
    public List<CandleEntity> saveAll(List<CandleEntity> candleEntities) {
        return candleRepository.saveAll(candleEntities);
    }

    public Optional<CandleEntity> findById(Long id) {
        return candleRepository.findById(id);
    }

    public boolean existsByInstrumentIdAndTimeframe(Long instrumentId, String timeframe) {
        return candleRepository.existsByInstrumentIdAndTimeframe(instrumentId, timeframe);
    }

    public Optional<Long> findOldestTimestampByInstrumentIdAndTimeframe(Long instrumentId, String timeframe) {
        return candleRepository.findOldestTimestampByInstrumentIdAndTimeframe(instrumentId, timeframe);
    }

    public Optional<Long> findNewestTimestampByInstrumentIdAndTimeframe(Long instrumentId, String timeframe) {
        return candleRepository.findNewestTimestampByInstrumentIdAndTimeframe(instrumentId, timeframe);
    }
}
