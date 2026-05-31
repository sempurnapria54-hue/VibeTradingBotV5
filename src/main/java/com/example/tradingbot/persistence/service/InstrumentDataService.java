package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Instrument}.
 */
@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;

    @Transactional
    public Instrument save(Instrument instrument) {
        return mapper.entityToDomain(repository.save(mapper.domainToEntity(instrument)));
    }

    @Transactional(readOnly = true)
    public Optional<Instrument> findById(Long id) {
        return repository.findById(id).map(mapper::entityToDomain);
    }

    @Transactional(readOnly = true)
    public Optional<Instrument> findByInternalId(String internalId) {
        return repository.findByInternalId(internalId).map(mapper::entityToDomain);
    }

    @Transactional(readOnly = true)
    public Optional<Instrument> findByExchangeIdAndExternalId(Long exchangeId, String externalId) {
        return repository.findByExchangeIdAndExternalId(exchangeId, externalId).map(mapper::entityToDomain);
    }

    @Transactional(readOnly = true)
    public List<Instrument> findByStatus(Instrument.Status status) {
        return repository.findByStatus(status).stream().map(mapper::entityToDomain).toList();
    }
}
