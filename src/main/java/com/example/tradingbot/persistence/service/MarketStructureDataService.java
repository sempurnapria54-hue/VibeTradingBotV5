package com.example.tradingbot.persistence.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.mapping.MarketStructureMapper;
import com.example.tradingbot.persistence.repository.MarketStructureRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для MarketStructure. Запись идемпотентна:
 * результат с уже присутствующим window_end_at в (instrument, config)
 * повторно не вставляется. При сломанной/изменившейся структуре job
 * сохраняет новый результат (новый window_end_at), а не правит старый.
 */
@Service
@RequiredArgsConstructor
public class MarketStructureDataService {

    private final MarketStructureRepository repository;
    private final MarketStructureMapper mapper;

    /** Сохраняет результат, если для (instrument, config, window_end_at) его ещё нет. */
    @Transactional
    public void saveIfNew(MarketStructure structure) {
        Boolean exists = repository.existsByInstrumentIdAndConfigIdAndWindowEndAt(
                structure.getInstrumentId(), structure.getConfigId(), structure.getWindowEndAt());
        if (isTrue(exists)) {
            return;
        }
        repository.save(mapper.domainToPersistence(structure));
    }

    /** Производный checkpoint: «докуда посчитано» для (instrument, config), или null. */
    @Transactional(readOnly = true)
    public OffsetDateTime findCheckpoint(Long instrumentId, Long configId) {
        return repository.findMaxWindowEndAt(instrumentId, configId);
    }

    /** Последняя по window_end_at структура конфигурации (для раздачи потребителям). */
    @Transactional(readOnly = true)
    public Optional<MarketStructure> findLatest(Long instrumentId, Long configId) {
        return repository.findFirstByInstrumentIdAndConfigIdOrderByWindowEndAtDesc(instrumentId, configId)
                .map(mapper::persistenceToDomain);
    }
}
