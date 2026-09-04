package com.example.tradingbot.persistence.service;

import static org.apache.commons.lang3.StringUtils.isBlank;

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
    private final TradeFeeRateDataService tradeFeeRateDataService;

    /**
     * Актуальные внешние правила инструмента; пусто — навес ещё не
     * материализован. Ставка комиссии ГИДРИРУЕТСЯ здесь: на навесе живёт
     * только ключ группы, а сама ставка — атрибут комиссионного уровня
     * счёта (docs/models/domain/other/TradeFeeRate.md). Тропы чтения
     * навеса две (контекст расчёта и преконтроль), и обе проходят через
     * эту границу — поэтому гидрирует она, а не каждый читатель.
     */
    @Transactional(readOnly = true)
    public Optional<InstrumentExternalRules> findByInstrumentId(Long instrumentId) {
        Optional<InstrumentExternalRules> rules = repository.findExternalRulesById(instrumentId)
                .map(converter::jsonToRules);
        rules.ifPresent(carried -> hydrateFeeRate(instrumentId, carried));
        return rules;
    }

    /**
     * Ставка группы по паре сырых значений внутри биржи инструмента.
     * Ключа группы нет либо группа не наблюдалась — поле остаётся пустым,
     * и потребитель отвергает действие: пустота нулём не подменяется.
     */
    private void hydrateFeeRate(Long instrumentId, InstrumentExternalRules rules) {
        if (isBlank(rules.getExternalFeeGroupId()) || isBlank(rules.getExternalInstrumentType())) {
            return;
        }
        repository.findExchangeIdById(instrumentId)
                .flatMap(exchangeId -> tradeFeeRateDataService.findCurrent(exchangeId,
                        rules.getExternalInstrumentType(), rules.getExternalFeeGroupId()))
                .ifPresent(rate -> rules.setExternalTakerFeeRate(rate.getExternalTakerFeeRate()));
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
