package com.example.marketdata.persistence.service;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.marketdata.mapping.MarketStructureMapper;
import com.example.marketdata.persistence.model.MarketStructureEntity;
import com.example.marketdata.persistence.repository.MarketStructureRepository;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для структуры рынка. Запись идемпотентна:
 * результат с уже присутствующим концом окна в паре (инструмент,
 * идентичность) повторно не вставляется. При изменившейся структуре
 * пишется НОВЫЙ результат (новый конец окна), а не правится старый: ряд
 * есть история, а не текущее значение.
 *
 * <p><b>Дедуп держит ключ, а не проверка перед вставкой</b>
 * (docs/rules/idempotency-via-unique.md): проверка «есть ли уже» и
 * следующая за ней вставка не атомарны, и второй писатель того же окна
 * падал бы нарушением ключа вместо того, чтобы его повтор поглотился.
 */
@Service
@RequiredArgsConstructor
public class MarketStructureDataService {

    private final MarketStructureRepository repository;
    private final MarketStructureMapper mapper;
    private final PointWriteAudit audit;

    /**
     * Сохраняет результат, если для (инструмент, идентичность, конец окна) его
     * ещё нет. Каркас заводится безопасной вставкой по ключу; уровни
     * дописываются к нему, только когда вставка состоялась, — у уже
     * записанного окна они свои и не трогаются.
     */
    @Transactional
    public void saveIfNew(MarketStructure structure) {
        MarketStructureEntity candidate = mapper.domainToPersistence(structure);
        Integer inserted = repository.insertIfAbsent(candidate.getInstrumentId(),
                candidate.getMarketStructureConfigId(), candidate.getType(), candidate.getWindowStartAt(),
                candidate.getWindowEndAt(), candidate.getConfirmedAt(), candidate.getBreakoutBrokenLevelType(),
                candidate.getBreakoutDirection(), candidate.getBreakoutLevelPrice(),
                candidate.getBreakoutConfirmedAt(), candidate.getExternalCreatedAt(),
                candidate.getExternalModifiedAt(), audit.moment(), audit.writer());
        if (inserted == 0) {
            return;
        }
        MarketStructureEntity frame = repository
                .findByInstrumentIdAndMarketStructureConfigIdAndWindowEndAt(candidate.getInstrumentId(),
                        candidate.getMarketStructureConfigId(), candidate.getWindowEndAt())
                .orElseThrow(() -> new IllegalStateException(
                        "Market structure frame is missing right after a safe insert: instrument "
                                + candidate.getInstrumentId() + ", config " + candidate.getMarketStructureConfigId()));
        frame.getLevels().addAll(emptyIfNull(candidate.getLevels()));
    }

    /** Последняя по концу окна структура идентичности. */
    @Transactional(readOnly = true)
    public Optional<MarketStructure> findLatest(Long instrumentId, Long marketStructureConfigId) {
        return repository
                .findFirstByInstrumentIdAndMarketStructureConfigIdOrderByWindowEndAtDesc(
                        instrumentId, marketStructureConfigId)
                .map(mapper::persistenceToDomain);
    }
}
