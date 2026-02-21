package com.example.tradingbot.domain.service;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.example.tradingbot.util.Constant.ErrorCode.INSTRUMENT_ALREADY_EXISTS;
import static com.example.tradingbot.util.Constant.ErrorCode.INSTRUMENT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentDataService instrumentDataService;
    private final ExchangeService exchangeService;

    public InstrumentEntity createInstrument(String exchangeInternalId, InstrumentEntity instrument, Set<String> timeFrames) {
        ExchangeEntity exchangeEntity = exchangeService.getRequiredByInternalId(exchangeInternalId);
        if (instrumentDataService.findByExchangeIdAndInstId(exchangeEntity.getId(), instrument.getExternalId()).isPresent()) {
            throw new RuntimeException(INSTRUMENT_ALREADY_EXISTS);
        }
        instrument.initOnCreate(exchangeEntity, timeFrames);
        return instrumentDataService.save(instrument);
    }

    public List<InstrumentEntity> getAllByExchange(String exchangeInternalId) {
        ExchangeEntity exchange = exchangeService.getRequiredByInternalId(exchangeInternalId);
        return instrumentDataService.findAllByExchangeId(exchange.getId());
    }

    public InstrumentEntity getRequiredByExchangeIdAndName(Long exchangeId, String name) {
        return instrumentDataService.findRequiredByExchangeIdAndName(exchangeId, name);
    }

    public InstrumentEntity getRequiredByExchangeIdAndInternalId(Long exchangeId, String internalId) {
        return instrumentDataService.findRequiredByExchangeIdAndInternalId(exchangeId, internalId);
    }

    public Long getRequiredIdByExchangeInternalIdAndInstrumentInternalId(String exchangeInternalId, String instrumentInternalId) {
        return instrumentDataService.findIdByExchangeInternalIdAndInstrumentInternalId(exchangeInternalId, instrumentInternalId)
            .orElseThrow(() -> new RuntimeException(INSTRUMENT_NOT_FOUND));
    }

    public InstrumentEntity getRequiredByExchangeInternalIdAndInstrumentInternalId(String exchangeInternalId, String instrumentInternalId) {
        ExchangeEntity exchange = exchangeService.getRequiredByInternalId(exchangeInternalId);
        return instrumentDataService.findRequiredByExchangeIdAndInternalId(exchange.getId(), instrumentInternalId);
    }
}
