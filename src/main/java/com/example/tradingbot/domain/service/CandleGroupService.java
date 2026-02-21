package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.rest.model.request.candlegroup.CreateCandleGroupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandleGroupService {

    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentService instrumentService;
    private final ExchangeDataService exchangeDataService;

    public List<CandleGroupEntity> getByInstrument(String exchangeInternalId, String instrumentInternalId) {
        Long instrumentId = instrumentService.getRequiredIdByExchangeInternalIdAndInstrumentInternalId(exchangeInternalId, instrumentInternalId);
        return candleGroupDataService.findAllByInstrumentId(instrumentId);
    }

    @Transactional
    public CandleGroupEntity create(String exchangeInternalId, String instrumentInternalId, CreateCandleGroupRequest request) {
        Long exchangeId = exchangeDataService.getRequiredIdByInternalId(exchangeInternalId);
        InstrumentEntity instrument = instrumentService.getRequiredByExchangeIdAndInternalId(exchangeId, instrumentInternalId);

        checkExistence(instrument.getId(), request.getTimeframe());

        var candleGroupEntity = new CandleGroupEntity();
        candleGroupEntity.initOnCreate(instrument, request);
        return candleGroupDataService.save(candleGroupEntity);
    }

    private void checkExistence(Long instrumentId, String timeFrame) {
        candleGroupDataService.checkNotExists(instrumentId, timeFrame);
    }
}
