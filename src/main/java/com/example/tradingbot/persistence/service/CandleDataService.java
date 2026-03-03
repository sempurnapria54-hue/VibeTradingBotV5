package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.CandleEntity;
import com.example.tradingbot.persistence.repository.CandleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandleDataService {

    private final CandleRepository candleRepository;

    @Transactional
    public int upsertBatch(Long groupId, List<CandleEntity> candles) {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }

        long minTs = candles.stream().mapToLong(CandleEntity::getOpenTimestamp).min().orElseThrow();
        long maxTs = candles.stream().mapToLong(CandleEntity::getOpenTimestamp).max().orElseThrow();

        Set<Long> existingTimestamps = new HashSet<>(
            candleRepository.findTimestampsByCandleGroupIdAndTimestampBetweenOrderByTimestampAsc(groupId, minTs, maxTs)
        );

        List<CandleEntity> toInsert = candles.stream()
            .filter(candle -> !existingTimestamps.contains(candle.getOpenTimestamp()))
            .toList();

        if (!toInsert.isEmpty()) {
            candleRepository.saveAll(toInsert);
        }

        return toInsert.size();
    }

    public long countBetween(Long groupId, long from, long to) {
        return candleRepository.countByCandleGroupIdAndTimestampBetween(groupId, from, to);
    }

    public List<Long> loadTimestamps(Long groupId, long from, long to) {
        return candleRepository.findTimestampsByCandleGroupIdAndTimestampBetweenOrderByTimestampAsc(groupId, from, to);
    }
}
