package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.search_params.InstrumentSearchParams;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import com.example.tradingbot.persistence.specification.InstrumentSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.INSTRUMENT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;

    @Transactional
    public Instrument save(Instrument instrument) {
        InstrumentEntity data = mapper.domainToData(instrument);
        InstrumentEntity saved = repository.save(data);
        return mapper.dataToDomain(saved);
    }

//    public void checkNotExists(Long exchangeId, String externalId) {
//        if (instrumentRepository.existsByExchangeIdAndExternalId(exchangeId, externalId)) {
//            throw new RuntimeException(INSTRUMENT_ALREADY_EXISTS);
//        }
//    }

    public Instrument findRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                         .map(mapper::dataToDomain)
                         .orElseThrow(() -> new RuntimeException(INSTRUMENT_NOT_FOUND));
    }

    public Instrument findRequiredById(Long id) {
        return repository.findById(id)
                         .map(mapper::dataToDomain)
                         .orElseThrow(() -> new RuntimeException(INSTRUMENT_NOT_FOUND));
    }

    public Instrument findRequiredByDealId(Long dealId) {
        return repository.findByDealId(dealId)
                         .map(mapper::dataToDomain)
                         .orElseThrow(() -> new RuntimeException(INSTRUMENT_NOT_FOUND));
    }

    public Page<Instrument> search(InstrumentSearchParams searchParams, Pageable pageable) {
        Page<InstrumentEntity> data =
                repository.findAll(InstrumentSpecification.bySearchParams(searchParams), pageable);
        return mapper.dataToDomain(data);
    }
}
