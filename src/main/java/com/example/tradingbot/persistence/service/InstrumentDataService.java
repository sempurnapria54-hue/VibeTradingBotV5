package com.example.tradingbot.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Instrument}. Здесь же —
 * fetch-or-throw чтения ({@code getRequiredBy*}). Доменный enum статуса
 * конвертируется в строку на границе репозитория.
 */
@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;

    @Transactional
    public Instrument save(Instrument instrument) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(instrument)));
    }

    @Transactional(readOnly = true)
    public Optional<Instrument> findById(Long id) {
        return repository.findById(id).map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public Instrument getRequiredById(Long id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    /**
     * Инструмент вместе с группами свечей (join fetch) — для проверок,
     * которым нужны группы (например {@code isReadyForActivation}).
     */
    @Transactional(readOnly = true)
    public Instrument getRequiredByIdWithCandleGroups(Long id) {
        return repository.findByIdWithCandleGroups(id)
                .map(mapper::persistenceToDomainWithCandleGroups)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    @Transactional(readOnly = true)
    public Instrument getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public List<Instrument> findByStatus(Instrument.Status status) {
        return repository.findByStatus(status.name()).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }
}
