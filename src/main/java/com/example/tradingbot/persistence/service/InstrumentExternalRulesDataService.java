package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.mapping.InstrumentExternalRulesJsonConverter;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import com.example.tradingbot.persistence.repository.InstrumentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link InstrumentExternalRules},
 * хранимых JSONB-навесом на строке-владельце instruments. Чтение — через
 * проекцию навеса (без вытягивания всей сущности); запись — load-modify
 * строки-владельца, чтобы audit-поля инструмента обновлялись штатным JPA
 * auditing. Собственной таблицы у правил нет — доступ только через
 * инструмент.
 */
@Service
@RequiredArgsConstructor
public class InstrumentExternalRulesDataService {

    private final InstrumentRepository repository;
    private final InstrumentExternalRulesJsonConverter converter;

    /** Актуальные внешние правила инструмента; пусто — навес ещё не материализован. */
    @Transactional(readOnly = true)
    public Optional<InstrumentExternalRules> findByInstrumentId(Long instrumentId) {
        return repository.findExternalRulesById(instrumentId).map(converter::jsonToRules);
    }

    /** Сохраняет/обновляет актуальный навес правил на строке-владельце. */
    @Transactional
    public void save(Long instrumentId, InstrumentExternalRules rules) {
        InstrumentEntity entity = repository.findById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + instrumentId));
        entity.setExternalRules(converter.rulesToJson(rules));
        repository.save(entity);
    }
}
