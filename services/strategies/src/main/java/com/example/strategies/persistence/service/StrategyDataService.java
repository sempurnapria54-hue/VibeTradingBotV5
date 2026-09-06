package com.example.strategies.persistence.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

import com.example.strategies.mapping.StrategyMapper;
import com.example.strategies.persistence.model.StrategyActionEntity;
import com.example.strategies.persistence.model.StrategyDetailEntity;
import com.example.strategies.persistence.model.StrategyEntity;
import com.example.strategies.persistence.model.StrategyStepEntity;
import com.example.strategies.persistence.model.StrategyTrancheEntity;
import com.example.strategies.persistence.repository.StrategyRepository;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для дерева определения у его владельца.
 *
 * <p><b>Числовых связей на чужие реестры здесь нет:</b> определение
 * называет счёт и инструмент идентичностями, и ключи чужих баз границу
 * сервиса не пересекают (docs/architecture/data-ownership.md
 * §Идентификаторы). Резолв в числовые FK — обязанность копии у ядра, не
 * владельца.
 */
@Service
@RequiredArgsConstructor
public class StrategyDataService {

    private final StrategyRepository repository;
    private final StrategyMapper mapper;

    /** Пишет дерево целиком и резолвит цели действий одной транзакцией. */
    @Transactional
    public Strategy saveTree(Strategy strategy) {
        StrategyEntity saved = repository.save(mapper.domainToPersistence(strategy));
        resolveTargetActions(saved);
        return mapper.persistenceToDomainWithTree(saved);
    }

    /** Корень без дерева — операции идентичности и статуса. */
    @Transactional(readOnly = true)
    public Optional<Strategy> findByInternalId(String internalId) {
        return repository.findByInternalId(internalId).map(mapper::persistenceToDomain);
    }

    /**
     * Определение со всем деревом: чтение поверхности и снимок, который
     * поедет содержимым события активации.
     */
    @Transactional(readOnly = true)
    public Optional<Strategy> findByInternalIdWithTree(String internalId) {
        return repository.findByInternalIdWithTree(internalId)
                .map(mapper::persistenceToDomainWithTree);
    }

    /**
     * Определения тенанта без дерева, окном от новых к старым — перечень
     * поверхности.
     *
     * @param limit размер окна; упор в него означает, что у тенанта
     *              определений больше, и постраничного обхода у перечня
     *              пока нет (`.claude/work/backlog.md` §«Постраничный
     *              обход перечня определений»)
     */
    @Transactional(readOnly = true)
    public List<Strategy> findByTenant(String tenantInternalId, Integer limit) {
        return repository.findByTenantInternalIdOrderByIdDesc(tenantInternalId, PageRequest.of(0, limit))
                .stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }

    /**
     * Идентичность активного определения пары; пусто — активного на паре
     * нет. Операнд инварианта «одна активная на паре» в приложении;
     * вторым носителем того же инварианта стои́т частичный уникальный
     * индекс.
     */
    @Transactional(readOnly = true)
    public Optional<String> findActiveInternalIdOnPair(String exchangeAccountInternalId,
                                                       String instrumentInternalId) {
        return repository.findActiveInternalIdOnPair(exchangeAccountInternalId, instrumentInternalId,
                Strategy.Status.ACTIVE.name());
    }

    /**
     * Строка определения по идентичности; нет — негодный вход вызова:
     * идентичность пришла снаружи.
     */
    @Transactional(readOnly = true)
    public StrategyEntity getRequiredEntityByInternalId(String internalId) {
        return repository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + internalId));
    }

    /**
     * Переставляет статус определения.
     *
     * <p><b>Транзакцию открывает вызывающий, а не этот метод.</b> Переход
     * статуса и строка outbox ложатся ОДНОЙ транзакцией
     * (docs/architecture/contracts.md §«У каждого класса события назван
     * писатель, и он же писатель решения»); собственная транзакция здесь
     * разорвала бы их надвое.
     */
    public void applyStatus(StrategyEntity entity, Strategy.Status status) {
        entity.setStatus(status.name());
        repository.save(entity);
    }

    private void resolveTargetActions(StrategyEntity saved) {
        if (isNull(saved.getDetails())) {
            return;
        }
        saved.getDetails().forEach(this::resolveDetailTargetActions);
    }

    /**
     * Резолв цели идёт по ВСЕЙ детали — шагам обоих уровней. Ключ действия
     * уникален в её пределах, и сужение области до одного уровня сделало
     * бы цель нерезолвимой ровно там, где она объявлена соседним.
     */
    private void resolveDetailTargetActions(StrategyDetailEntity detail) {
        List<StrategyActionEntity> actions = detailActions(detail);
        Map<String, StrategyActionEntity> actionsByKey = new HashMap<>();
        actions.forEach(action -> actionsByKey.put(action.getKey(), action));
        actions.stream()
                .filter(action -> nonNull(action.getTargetActionKey()))
                .forEach(action -> action.setTargetAction(requiredTarget(actionsByKey, action)));
    }

    private StrategyActionEntity requiredTarget(Map<String, StrategyActionEntity> actionsByKey,
                                                StrategyActionEntity action) {
        StrategyActionEntity target = actionsByKey.get(action.getTargetActionKey());
        if (isNull(target)) {
            throw new IllegalStateException(
                    "Unresolved targetActionKey after validation: " + action.getTargetActionKey());
        }
        return target;
    }

    /** Все действия детали: с шагов объявлений траншей и с шагов узкой агрегатной поверхности. */
    private List<StrategyActionEntity> detailActions(StrategyDetailEntity detail) {
        List<StrategyStepEntity> steps = new ArrayList<>();
        if (nonNull(detail.getSteps())) {
            steps.addAll(detail.getSteps());
        }
        if (nonNull(detail.getTranches())) {
            detail.getTranches().stream()
                    .map(StrategyTrancheEntity::getSteps)
                    .filter(Objects::nonNull)
                    .forEach(steps::addAll);
        }
        return steps.stream()
                .filter(step -> nonNull(step.getActions()))
                .flatMap(step -> step.getActions().stream())
                .collect(toList());
    }
}
