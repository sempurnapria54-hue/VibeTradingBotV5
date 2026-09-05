package com.example.tradingcore.persistence.service;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import com.example.tradingcore.mapping.InstrumentMapper;
import com.example.tradingcore.persistence.model.InstrumentEntity;
import com.example.tradingcore.persistence.repository.InstrumentRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для проекции каталога инструментов.
 *
 * <p><b>Спецификация и правила кладутся ОДНИМ ходом, и это условие
 * гейта.</b> Момент снимка описывает строку целиком; двинуть его,
 * записав половину, значило бы объявить свежими правила, которых не
 * читали (docs/models/domain/core/Instrument.md §«Срок свежести проекции:
 * величина, писатель, реакция»).
 */
@Service
@RequiredArgsConstructor
public class InstrumentDataService {

    private final InstrumentRepository repository;
    private final InstrumentMapper mapper;
    private final InstrumentExternalRulesJsonConverter rulesConverter;

    /**
     * Сводит строку проекции с каталогом владельца: заводит недостающую,
     * обновляет спецификацию и навес правил существующей.
     *
     * @param instrument  инструмент, каким его отдал каталог
     * @param rules       справочные правила; пусто — навес у владельца
     *                    ещё не материализован
     * @param projectedAt момент снимка строки целиком
     */
    @Transactional
    public void upsertProjection(Instrument instrument, InstrumentExternalRules rules,
                                 OffsetDateTime projectedAt) {
        InstrumentEntity entity = repository.findByInternalId(instrument.getInternalId())
                .orElseGet(() -> newProjection(instrument));
        mapper.updateProjection(instrument, entity);
        entity.setExternalRules(rulesConverter.rulesToJson(rules));
        entity.setProjectedAt(projectedAt);
        repository.save(entity);
    }

    /**
     * Заводит строку неизменяемой частью: идентичность инструмента —
     * владельца каталога, ядро своей не назначает.
     */
    private InstrumentEntity newProjection(Instrument instrument) {
        InstrumentEntity entity = new InstrumentEntity();
        entity.setInternalId(instrument.getInternalId());
        entity.setExchangeCode(instrument.getExchangeCode());
        entity.setExternalId(instrument.getExternalId());
        return entity;
    }
}
