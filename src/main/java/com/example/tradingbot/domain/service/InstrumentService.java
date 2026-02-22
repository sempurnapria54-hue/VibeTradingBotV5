package com.example.tradingbot.domain.service;

import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.rest.model.request.CreateInstrumentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.tradingbot.util.Constant.ErrorCode.INSTRUMENT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentDataService instrumentDataService;
    private final ExchangeDataService exchangeDataService;

    public InstrumentEntity createInstrument(String exchangeInternalId, CreateInstrumentRequest request) {
        var exchangeId = exchangeDataService.getRequiredIdByInternalId(exchangeInternalId);
        checkExistence(exchangeId, request.getExternalId());
        var instrumentEntity = new InstrumentEntity();
        instrumentEntity.initOnCreate(exchangeId, request);
        return instrumentDataService.save(instrumentEntity);
    }

    public List<InstrumentEntity> getAllByExchange(String exchangeInternalId) {
        var exchangeId = exchangeDataService.getRequiredIdByInternalId(exchangeInternalId);
        return instrumentDataService.findAllByExchangeId(exchangeId);
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
        var exchangeId = exchangeDataService.getRequiredIdByInternalId(exchangeInternalId);
        return instrumentDataService.findRequiredByExchangeIdAndInternalId(exchangeId, instrumentInternalId);
    }

    private void checkExistence(Long exchangeId, String externalId) {
        instrumentDataService.checkNotExists(exchangeId, externalId);
    }

}
