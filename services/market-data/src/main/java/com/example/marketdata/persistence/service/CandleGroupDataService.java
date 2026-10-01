package com.example.marketdata.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.marketdata.mapping.CandleGroupMapper;
import com.example.marketdata.persistence.repository.CandleGroupRepository;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для единиц сбора свечей. Доменные перечни
 * (таймфрейм, статус) конвертируются в строки на границе репозитория.
 */
@Service
@RequiredArgsConstructor
public class CandleGroupDataService {

    private final CandleGroupRepository repository;
    private final CandleGroupMapper mapper;
    private final PointWriteAudit audit;

    @Transactional
    public CandleGroup save(CandleGroup group) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(group)));
    }

    /**
     * Итог шага цикла загрузки — статус, счёт, фактические границы и
     * попытки докачки — точечной записью, если статус и горизонт группы
     * остались теми, что шаг застал. Горизонт не пишется: его писатель —
     * приём требования потребителя (docs/lifecycles/CandleGroup.md §«Возврат
     * к `BACKFILL` по углублённому требованию»).
     *
     * @param group         группа с итогом шага
     * @param loadedStatus  статус, застанный в начале шага
     * @param loadedHorizon горизонт, застанный в начале шага; пусто — «вся история»
     * @return записан ли итог: ложь — группу переписали посреди шага
     */
    @Transactional
    public Boolean saveLoadingStep(CandleGroup group, CandleGroup.Status loadedStatus, Long loadedHorizon) {
        Integer written = repository.applyLoadingStep(group.getId(), group.getStatus().name(), group.getCount(),
                group.getActualFirstUtcMillis(), group.getActualLastUtcMillis(), group.getRepairAttempts(),
                loadedStatus.name(), loadedHorizon, audit.moment(), audit.writer());
        return written > 0;
    }

    /**
     * Углублённый горизонт и статус, выведенный из него, — точечной
     * записью, если статус и горизонт группы остались теми, что приём
     * требования застал. Счёт, границы и попытки докачки не пишутся: их
     * писатель — шаг цикла загрузки (docs/lifecycles/CandleGroup.md
     * §«Возврат к `BACKFILL` по углублённому требованию»).
     *
     * @param group         группа с углублённым горизонтом и статусом
     * @param loadedStatus  статус, застанный приёмом требования
     * @param loadedHorizon горизонт, застанный приёмом требования; пусто — «вся история»
     * @return записан ли горизонт: ложь — шаг цикла переписал группу между чтением и записью
     */
    @Transactional
    public Boolean saveDeepenedHorizon(CandleGroup group, CandleGroup.Status loadedStatus, Long loadedHorizon) {
        Integer written = repository.applyDeepenedHorizon(group.getId(), group.getPlannedFirstUtcMillis(),
                group.getStatus().name(), loadedStatus.name(), loadedHorizon, audit.moment(), audit.writer());
        return written > 0;
    }

    @Transactional(readOnly = true)
    public CandleGroup getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Candle group not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<CandleGroup> findByInstrumentIdAndTimeframe(Long instrumentId, TimeFrame timeframe) {
        return repository.findByInstrumentIdAndTimeframe(instrumentId, timeframe.name())
                .map(mapper::persistenceToDomain);
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByInstrumentId(Long instrumentId) {
        return repository.findByInstrumentId(instrumentId).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    @Transactional(readOnly = true)
    public List<CandleGroup> findByStatusIn(Collection<CandleGroup.Status> statuses) {
        return repository.findByStatusIn(names(statuses)).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /**
     * Группы таймфрейма, готовые отдать историю расчёту. Популяция
     * производных: идентичность заказана глобально, а инструменты
     * приносят те группы, что уже собраны.
     */
    @Transactional(readOnly = true)
    public List<CandleGroup> findByTimeframeAndStatusIn(TimeFrame timeframe,
                                                        Collection<CandleGroup.Status> statuses) {
        return repository.findByTimeframeAndStatusIn(timeframe.name(), names(statuses)).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /** Инструменты, у которых хотя бы одна группа ещё не готова. */
    @Transactional(readOnly = true)
    public List<Long> findInstrumentIdsWithUnreadyGroups() {
        return repository.findInstrumentIdsWithUnreadyGroups(CandleGroup.Status.ACTIVE.name());
    }

    private List<String> names(Collection<CandleGroup.Status> statuses) {
        return statuses.stream().map(Enum::name).collect(toList());
    }
}
