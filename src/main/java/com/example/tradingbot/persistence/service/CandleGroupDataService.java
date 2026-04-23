package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.mapping.CandleGroupMapper;
import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import com.example.tradingbot.persistence.repository.CandleGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.example.tradingbot.util.Constant.ErrorCode.CANDLE_GROUP_ALREADY_EXISTS;

@Service
@RequiredArgsConstructor
public class CandleGroupDataService {

    private final CandleGroupRepository candleGroupRepository;
    private final CandleGroupMapper candleGroupMapper;

    public void checkNotExists(Long instrumentId, String timeframe) {
        if (candleGroupRepository.existsByInstrumentIdAndTimeframe(instrumentId, timeframe)) {
            throw new RuntimeException(CANDLE_GROUP_ALREADY_EXISTS);
        }
    }

    public CandleGroup save(CandleGroup candleGroup) {
        CandleGroupEntity candleGroupEntity = candleGroupMapper.domainToData(candleGroup);
        CandleGroupEntity saved = candleGroupRepository.save(candleGroupEntity);
        return candleGroupMapper.dataToDomain(saved);
    }
}
