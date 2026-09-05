package com.example.marketdata.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.marketdata.mapping.InstrumentMapper;
import com.example.marketdata.persistence.model.InstrumentEntity;
import com.example.marketdata.persistence.repository.InstrumentRepository;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для каталога инструментов; здесь же
 * fetch-or-throw чтения. Доменный перечень статуса конвертируется в
 * строку на границе репозитория.
 *
 * <p>Код площадки на доменной модели живёт своим полем и через границу
 * едет как есть; числовой идентификатор площадки market-data не пишет
 * (docs/models/domain/core/Instrument.md).
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

    /**
     * Сохраняет спецификацию из листинга, не трогая навес правил: он
     * лежит своей колонкой и обновляется своим тиком, а перенос полей его
     * бы затёр пустотой.
     */
    @Transactional
    public Instrument saveSpecification(Instrument instrument) {
        InstrumentEntity entity = repository.findById(instrument.getId())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + instrument.getId()));
        InstrumentEntity mapped = mapper.domainToPersistence(instrument);
        mapped.setExternalRules(entity.getExternalRules());
        return mapper.persistenceToDomain(repository.save(mapped));
    }

    @Transactional(readOnly = true)
    public Optional<Instrument> findById(Long id) {
        return repository.findById(id).map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public Instrument getRequiredById(Long id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    @Transactional(readOnly = true)
    public Instrument getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + internalId));
    }

    @Transactional(readOnly = true)
    public Optional<Instrument> findByExternalId(String exchangeCode, String externalId) {
        return repository.findByExchangeCodeAndExternalId(exchangeCode, externalId)
                .map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public List<Instrument> findByStatusIn(Collection<Instrument.Status> statuses) {
        return repository.findByStatusIn(names(statuses)).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /**
     * Популяция прохода сбора срезов: действующий листинг площадки
     * стабильным порядком и ограниченным окном. Порядок по
     * идентификатору — при нехватке бюджета усечённым оказывается один и
     * тот же хвост, а не случайные инструменты
     * (docs/processes/snapshot-collection.md).
     */
    @Transactional(readOnly = true)
    public List<Instrument> findListedWithin(String exchangeCode, Collection<Instrument.Status> statuses,
                                             Integer limit) {
        return repository
                .findByExchangeCodeAndStatusInOrderByIdAsc(exchangeCode, names(statuses), PageRequest.of(0, limit))
                .stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /**
     * Окно листинга за курсором: обход по кругу для работ, которым нужен
     * каждый инструмент, но не за один тик.
     */
    @Transactional(readOnly = true)
    public List<Instrument> findListedAfter(String exchangeCode, Collection<Instrument.Status> statuses,
                                            Long cursorId, Integer limit) {
        return repository
                .findByExchangeCodeAndStatusInAndIdGreaterThanOrderByIdAsc(
                        exchangeCode, names(statuses), cursorId, PageRequest.of(0, limit))
                .stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /** Проекция: только internalId по идентификатору — сущность не тянется. */
    @Transactional(readOnly = true)
    public String getRequiredInternalIdById(Long id) {
        return repository.findInternalIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + id));
    }

    /** Проекция: только идентификатор по internalId — сущность не тянется. */
    @Transactional(readOnly = true)
    public Long getRequiredIdByInternalId(String internalId) {
        return repository.findIdByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + internalId));
    }

    private List<String> names(Collection<Instrument.Status> statuses) {
        return statuses.stream().map(Enum::name).collect(toList());
    }
}
