package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup.Status;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.mapping.CandleGroupMapper;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandleGroupService {

    private final CandleGroupDataService candleGroupDataService;
    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final CandleGroupMapper candleGroupMapper;

    public List<CandleGroup> getByInstrument(String instrumentInternalId) {
        Instrument instrument = instrumentDataService.findRequiredByInternalId(instrumentInternalId);
        return instrument.getCandleGroups();
    }

    @Transactional
    public CandleGroup create(String exchangeInternalId,
                              String instrumentInternalId,
                              CandleGroup request) {
        Exchange exchange = exchangeDataService.findRequiredByInternalId(exchangeInternalId);
        Instrument instrument = instrumentDataService.findRequiredByInternalId(instrumentInternalId);

        checkExistence(instrument.getId(), request.getTimeframe());

        CandleGroup candleGroup = new CandleGroup();
        candleGroupMapper.domainToDomainOnCreate(request, candleGroup);
        candleGroup.setStatus(Status.CREATED);
        return candleGroupDataService.save(candleGroup);
    }

    private void checkExistence(Long instrumentId, String timeFrame) {
        candleGroupDataService.checkNotExists(instrumentId, timeFrame);
    }
}
