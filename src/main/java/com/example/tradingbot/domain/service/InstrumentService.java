package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.example.tradingbot.util.Constant.ErrorCode.INSTRUMENT_ALREADY_EXISTS;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentDataService instrumentDataService;
    private final ExchangeService exchangeService;

    public InstrumentEntity createInstrument(String exchangeName, InstrumentEntity instrument, Set<String> timeFrames) {
        ExchangeEntity exchangeEntity = exchangeService.getRequiredByName(exchangeName);
        if (instrumentDataService.findByExchangeIdAndInstId(exchangeEntity.getId(), instrument.getExternalId()).isPresent()) {
            throw new RuntimeException(INSTRUMENT_ALREADY_EXISTS);
        }
        instrument.initOnCreate(exchangeEntity, timeFrames);
        return instrumentDataService.save(instrument);
    }

    public List<InstrumentEntity> getAllByExchange(String exchangeName) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        return instrumentDataService.findAllByExchangeId(exchange.getId());
    }

    public InstrumentEntity getRequiredByExchangeNameAndName(String exchangeName, String instrumentName) {
        ExchangeEntity exchange = exchangeService.getRequiredByName(exchangeName);
        return getRequiredByExchangeIdAndName(exchange.getId(), instrumentName);
    }

    public InstrumentEntity getRequiredByExchangeIdAndName(Long exchangeId, String name) {
        return instrumentDataService.findRequiredByExchangeIdAndName(exchangeId, name);
    }
}
