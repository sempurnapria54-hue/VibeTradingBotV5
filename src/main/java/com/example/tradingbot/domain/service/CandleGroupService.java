package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.CANDLE_GROUP_ALREADY_EXISTS;

@Service
@RequiredArgsConstructor
public class CandleGroupService {

    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentService instrumentService;
    private final ExchangeService exchangeService;

    public List<CandleGroupEntity> listByInstrument(String exchangeName, String instrumentName) {
        var instrumentEntity = instrumentService.getRequiredByExchangeNameAndName(exchangeName, instrumentName);
        return candleGroupDataService.findAllByInstrumentId(instrumentEntity.getId());
    }

    @Transactional
    public CandleGroupEntity create(String exchangeName, String instrumentName, CandleGroupEntity candleGroup) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);

        if (candleGroupDataService.findByInstrumentIdAndTimeframe(instrument.getId(), candleGroup.getTimeframe()).isPresent()) {
            throw new RuntimeException(CANDLE_GROUP_ALREADY_EXISTS);
        }
        candleGroup.initOnCreate(instrument);
        return candleGroupDataService.save(candleGroup);
    }
}
