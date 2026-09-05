package com.example.marketdata.persistence.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.marketdata.mapping.MarketStructureMapper;
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
 */
@Service
@RequiredArgsConstructor
public class MarketStructureDataService {

    private final MarketStructureRepository repository;
    private final MarketStructureMapper mapper;

    /** Сохраняет результат, если для (инструмент, идентичность, конец окна) его ещё нет. */
    @Transactional
    public void saveIfNew(MarketStructure structure) {
        Boolean exists = repository.existsByInstrumentIdAndMarketStructureConfigIdAndWindowEndAt(
                structure.getInstrumentId(), structure.getMarketStructureConfigId(), structure.getWindowEndAt());
        if (isTrue(exists)) {
            return;
        }
        repository.save(mapper.domainToPersistence(structure));
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
