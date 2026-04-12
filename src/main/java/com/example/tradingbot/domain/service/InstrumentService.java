package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.Instrument.Status;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.search_params.InstrumentSearchParams;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentMapper instrumentMapper;

    public Instrument createInstrument(String exchangeInternalId, Instrument request) {
        Exchange exchange = exchangeDataService.findRequiredByInternalId(exchangeInternalId);

        checkExistence(exchange.getId(), request.getExternalId());

        Instrument instrument = new Instrument();
        instrumentMapper.domainToDomainOnCreate(request, instrument);
        instrument.setStatus(Status.ACTIVE);
        return instrumentDataService.save(instrument);
    }


    private void checkExistence(Long exchangeId, String externalId) {
        instrumentDataService.checkNotExists(exchangeId, externalId);
    }

    public Instrument getByInternalId(String instrumentInternalId) {
        return instrumentDataService.findRequiredByInternalId(instrumentInternalId);
    }

    public Instrument getRequiredById(Long instrumentId) {
        return instrumentDataService.findRequiredById(instrumentId);
    }

    public Page<Instrument> getByParams(InstrumentSearchParams searchParams, Pageable pageable) {
        return instrumentDataService.search(searchParams, pageable);
    }

    public void blockInstrument(Instrument instrument) {
        instrument.setStatus(Status.ERROR);
        instrumentDataService.save(instrument);
    }

    public void holdInstrument(Instrument instrument) {
        instrument.setStatus(Status.HOLD);
        instrumentDataService.save(instrument);
    }

    public void activateInstrument(Instrument instrument) {
        instrument.setStatus(Status.ACTIVE);
        instrumentDataService.save(instrument);
    }
}
