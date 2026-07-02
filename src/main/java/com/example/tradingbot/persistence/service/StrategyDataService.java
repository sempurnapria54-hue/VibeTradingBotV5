package com.example.tradingbot.persistence.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.mapping.StrategyMapper;
import com.example.tradingbot.persistence.model.strategy.StrategyActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyDetailEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import com.example.tradingbot.persistence.repository.StrategyDetailRepository;
import com.example.tradingbot.persistence.repository.StrategyRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link Strategy}. Здесь же —
 * fetch-or-throw чтения и резолв self-ссылки действий: после вставки
 * дерева targetActionKey резолвится в self-FK target_action_id (ключ →
 * действие в рамках той же детали; правка управляемых сущностей до
 * commit). Доменные enum'ы конвертируются в строки на границе
 * репозитория; JSONB-навес — через StrategyJsonConverter в маппере.
 */
@Service
@RequiredArgsConstructor
public class StrategyDataService {

    private final StrategyRepository repository;
    private final StrategyDetailRepository detailRepository;
    private final StrategyMapper mapper;

    /** Сохраняет полное дерево и резолвит target-ссылки действий (в одной транзакции). */
    @Transactional
    public Strategy save(Strategy strategy) {
        StrategyEntity saved = repository.save(mapper.domainToPersistence(strategy));
        resolveTargetActions(saved);
        return mapper.persistenceToDomainWithTree(saved);
    }

    /** Корень без дерева — для операций статуса/идентичности. */
    @Transactional(readOnly = true)
    public Strategy getRequiredByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + internalId));
    }

    /** Стратегия со всем деревом (join fetch). */
    @Transactional(readOnly = true)
    public Strategy getRequiredByInternalIdWithTree(String internalId) {
        return repository.findByInternalIdWithTree(internalId)
                .map(mapper::persistenceToDomainWithTree)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + internalId));
    }

    /** Смена административного статуса без перезаписи дерева (immutable-правила не трогаются). */
    @Transactional
    public void updateStatus(String internalId, Strategy.Status status) {
        StrategyEntity entity = repository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + internalId));
        entity.setStatus(status.name());
        repository.save(entity);
    }

    /** Есть ли у инструмента активная стратегия (инвариант: одна ACTIVE на инструмент). */
    @Transactional(readOnly = true)
    public Boolean existsActiveByInstrumentId(Long instrumentId) {
        return repository.existsByInstrumentIdAndStatus(instrumentId, Strategy.Status.ACTIVE.name());
    }

    /**
     * Стратегии всех статусов кроме DELETED с настройками рыночных данных
     * (фаза + детали, без шагов) — вход jobs расчёта индикаторов/
     * структуры/фазы.
     */
    @Transactional(readOnly = true)
    public List<Strategy> findForMarketData() {
        return repository.findAllWithSettingsByStatusNot(Strategy.Status.DELETED.name()).stream()
                .map(mapper::persistenceToDomainWithSettings)
                .collect(toList());
    }

    /**
     * Активная стратегия инструмента с настройками рыночных данных (без
     * шагов/деталей) — вход калькулятора для резолва индикаторов/структуры/
     * фазы по ключу. Пусто — активной стратегии у инструмента нет.
     */
    @Transactional(readOnly = true)
    public Optional<Strategy> findActiveByInstrumentIdWithSettings(Long instrumentId) {
        return repository.findByInstrumentIdAndStatusWithSettings(instrumentId, Strategy.Status.ACTIVE.name())
                .stream()
                .findFirst()
                .map(mapper::persistenceToDomainWithSettings);
    }

    /**
     * Активная стратегия инструмента со всем деревом (детали + шаги +
     * действия) — вход entry-скана. Пусто — активной стратегии у инструмента
     * нет.
     */
    @Transactional(readOnly = true)
    public Optional<Strategy> findActiveByInstrumentIdWithTree(Long instrumentId) {
        return repository.findByInstrumentIdAndStatusWithTree(instrumentId, Strategy.Status.ACTIVE.name())
                .map(mapper::persistenceToDomainWithTree);
    }

    /**
     * Запиненная {@link StrategyDetail} сделки по id со своим деревом (шаги +
     * действия), <b>без</b> привязки к статусу родительской стратегии. Сборка
     * контекста уже открытой сделки опирается на снимок настроек, запиненный
     * при открытии, а не на живую активную стратегию: сопровождение и аварийное
     * закрытие работают одинаково при {@code Strategy.INACTIVE} и {@code DELETED}.
     */
    @Transactional(readOnly = true)
    public StrategyDetail getRequiredDetailByIdWithTree(Long detailId) {
        return detailRepository.findByIdWithTree(detailId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalStateException("Pinned StrategyDetail not found: " + detailId));
    }

    private void resolveTargetActions(StrategyEntity saved) {
        if (isNull(saved.getDetails())) {
            return;
        }
        saved.getDetails().forEach(this::resolveDetailTargetActions);
    }

    private void resolveDetailTargetActions(StrategyDetailEntity detail) {
        if (isNull(detail.getSteps())) {
            return;
        }
        Map<String, StrategyActionEntity> actionsByKey = new HashMap<>();
        detail.getSteps().stream()
                .filter(step -> nonNull(step.getActions()))
                .forEach(step -> step.getActions()
                        .forEach(action -> actionsByKey.put(action.getKey(), action)));
        detail.getSteps().stream()
                .filter(step -> nonNull(step.getActions()))
                .forEach(step -> step.getActions().forEach(action -> {
            if (nonNull(action.getTargetActionKey())) {
                StrategyActionEntity target = actionsByKey.get(action.getTargetActionKey());
                if (isNull(target)) {
                    throw new IllegalStateException(
                            "Unresolved targetActionKey after validation: " + action.getTargetActionKey());
                }
                action.setTargetAction(target);
            }
        }));
    }
}
