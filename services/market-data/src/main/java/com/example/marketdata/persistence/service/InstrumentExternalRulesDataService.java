package com.example.marketdata.persistence.service;

import com.example.marketdata.mapping.InstrumentExternalRulesJsonConverter;
import com.example.marketdata.persistence.model.InstrumentEntity;
import com.example.marketdata.persistence.repository.InstrumentRepository;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для справочных правил инструмента,
 * хранимых JSONB-навесом на строке-владельце. Чтение — через проекцию
 * навеса (без вытягивания всей сущности); запись — load-modify
 * строки-владельца, чтобы audit-поля обновлял штатный JPA auditing.
 * Собственной таблицы у правил нет — доступ только через инструмент.
 *
 * <p><b>Ставка комиссии здесь НЕ гидрируется, и это не потеря.</b> Она
 * атрибут комиссионного уровня СЧЁТА
 * (docs/models/domain/other/TradeFeeRate.md), читается с ключами счёта и
 * потому market-data недоступна: его чтения площадки публичные
 * (docs/architecture/contracts.md §«Синхронные вызовы»). Навес несёт
 * только КЛЮЧ комиссионной группы; ставку по этому ключу резолвит тот,
 * у кого счёт есть.
 */
@Service
@RequiredArgsConstructor
public class InstrumentExternalRulesDataService {

    private final InstrumentRepository repository;
    private final InstrumentExternalRulesJsonConverter converter;

    /** Актуальные справочные правила инструмента; пусто — навес ещё не материализован. */
    @Transactional(readOnly = true)
    public Optional<InstrumentExternalRules> findByInstrumentId(Long instrumentId) {
        return repository.findExternalRulesById(instrumentId).map(converter::jsonToRules);
    }

    /** Сохраняет актуальный навес правил на строке-владельце. */
    @Transactional
    public void save(Long instrumentId, InstrumentExternalRules rules) {
        InstrumentEntity entity = repository.findById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + instrumentId));
        entity.setExternalRules(converter.rulesToJson(rules));
        repository.save(entity);
    }
}
