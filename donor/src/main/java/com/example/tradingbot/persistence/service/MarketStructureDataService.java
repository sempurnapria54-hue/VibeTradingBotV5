package com.example.tradingbot.persistence.service;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.mapping.MarketStructureMapper;
import com.example.tradingbot.persistence.repository.MarketStructureRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для MarketStructure. Запись идемпотентна:
 * результат с уже присутствующим window_end_at в (instrument, setting)
 * повторно не вставляется. При сломанной/изменившейся структуре job
 * сохраняет новый результат (новый window_end_at), а не правит старый.
 * Ключ — настройка-владелец (owner-ключевание, трек D).
 */
@Service
@RequiredArgsConstructor
public class MarketStructureDataService {

    private final MarketStructureRepository repository;
    private final MarketStructureMapper mapper;

    /** Сохраняет результат, если для (instrument, setting, window_end_at) его ещё нет. */
    @Transactional
    public void saveIfNew(MarketStructure structure) {
        Boolean exists = repository.existsByInstrumentIdAndStrategyMarketStructureSettingIdAndWindowEndAt(
                structure.getInstrumentId(), structure.getStrategyMarketStructureSettingId(),
                structure.getWindowEndAt());
        if (isTrue(exists)) {
            return;
        }
        repository.save(mapper.domainToPersistence(structure));
    }

    /** Последняя по window_end_at структура настройки (для раздачи потребителям). */
    @Transactional(readOnly = true)
    public Optional<MarketStructure> findLatest(Long instrumentId, Long strategyMarketStructureSettingId) {
        return repository
                .findFirstByInstrumentIdAndStrategyMarketStructureSettingIdOrderByWindowEndAtDesc(
                        instrumentId, strategyMarketStructureSettingId)
                .map(mapper::persistenceToDomain);
    }
}
