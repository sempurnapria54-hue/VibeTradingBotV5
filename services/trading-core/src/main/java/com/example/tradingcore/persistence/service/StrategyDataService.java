package com.example.tradingcore.persistence.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingcore.mapping.StrategyMapper;
import com.example.tradingcore.persistence.model.StrategyActionEntity;
import com.example.tradingcore.persistence.model.StrategyDetailEntity;
import com.example.tradingcore.persistence.model.StrategyEntity;
import com.example.tradingcore.persistence.model.StrategyStepEntity;
import com.example.tradingcore.persistence.model.StrategyTrancheEntity;
import com.example.tradingcore.persistence.repository.StrategyDetailRepository;
import com.example.tradingcore.persistence.repository.StrategyIndicatorSettingRepository;
import com.example.tradingcore.persistence.repository.StrategyMarketStructureSettingRepository;
import com.example.tradingcore.persistence.repository.StrategyRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для копии определения стратегии.
 *
 * <p>Здесь же — резолв само-ссылки действий: после вставки дерева
 * логический ключ цели переводится в отложенный FK. Резолв идёт ПОСЛЕ
 * вставки и в той же транзакции, потому что идентификаторов у действий до
 * вставки не существует, а ключи известны автору.
 */
@Service
@RequiredArgsConstructor
public class StrategyDataService {

    private final StrategyRepository repository;
    private final StrategyDetailRepository detailRepository;
    private final StrategyIndicatorSettingRepository indicatorSettingRepository;
    private final StrategyMarketStructureSettingRepository marketStructureSettingRepository;
    private final StrategyMapper mapper;
    private final ExchangeAccountDataService exchangeAccountDataService;
    private final InstrumentDataService instrumentDataService;

    /**
     * Пишет копию дерева целиком и резолвит одной транзакцией и цели
     * действий, и числовые связи контекста.
     *
     * <p><b>Идентичности контекста резолвятся ЗДЕСЬ, на границе
     * domain → persistence.</b> Определение адресует счёт и инструмент
     * идентичностями — числовые ключи баз границу сервиса не пересекают
     * (docs/architecture/data-ownership.md §Идентификаторы), — а строки
     * ядра связываются числами внутри его базы. Резолв стои́т у границы,
     * а не у писателя копии: писателей у неё за жизнь два (команда приёма
     * сегодня, потребитель события активации после шага 8), и каждому
     * пришлось бы помнить об одном и том же.
     *
     * <p><b>Нерезолвенная идентичность — отказ приёма, а не пустая
     * ссылка:</b> копия без счёта или инструмента не читается отбором
     * входа вовсе (docs/models/mapping/Strategy.md).
     */
    @Transactional
    public Strategy saveTree(Strategy strategy) {
        StrategyEntity entity = mapper.domainToPersistence(strategy);
        entity.setExchangeAccountId(
                exchangeAccountDataService.getRequiredIdByInternalId(strategy.getExchangeAccountInternalId()));
        entity.setInstrumentId(
                instrumentDataService.getRequiredIdByInternalId(strategy.getInstrumentInternalId()));
        StrategyEntity saved = repository.save(entity);
        resolveTargetActions(saved);
        return mapper.persistenceToDomainWithTree(saved);
    }

    /** Корень копии без дерева — операции идентичности и статуса. */
    @Transactional(readOnly = true)
    public Optional<Strategy> findByInternalId(String internalId) {
        return repository.findByInternalId(internalId).map(mapper::persistenceToDomain);
    }

    /**
     * Активная стратегия пары «счёт, инструмент» со всем деревом. Пусто —
     * активной стратегии на паре нет, и это штатный ответ: инструмент без
     * стратегии сканер входа пропускает.
     */
    @Transactional(readOnly = true)
    public Optional<Strategy> findActiveOnPairWithTree(Long exchangeAccountId, Long instrumentId) {
        return repository.findByPairAndStatusWithTree(exchangeAccountId, instrumentId,
                        Strategy.Status.ACTIVE.name())
                .map(mapper::persistenceToDomainWithTree);
    }

    /**
     * Закреплённая деталь сделки со своим поддеревом. Ненайденность здесь
     * — авария тропы: сделка ссылается на деталь, которой в копии нет,
     * и вести её не по чему.
     */
    @Transactional(readOnly = true)
    public StrategyDetail getRequiredDetailByIdWithTree(Long detailId) {
        return detailRepository.findByIdWithTree(detailId)
                .map(mapper::persistenceToDomain)
                .orElseThrow(() -> new IllegalStateException("Pinned StrategyDetail not found: " + detailId));
    }

    /**
     * Копии, у которых есть объявление каталога без идентичности
     * вычисления, — вход тика объявления потребности у владельца рыночных
     * данных. Объединение двух перечней: одна и та же копия может быть
     * непривязана по любому из двух видов объявлений.
     */
    @Transactional(readOnly = true)
    public List<Long> findIdsWithUnboundComputation() {
        Set<Long> ids = new LinkedHashSet<>(indicatorSettingRepository.findStrategyIdsWithUnboundComputation());
        ids.addAll(marketStructureSettingRepository.findStrategyIdsWithUnboundComputation());
        return new ArrayList<>(ids);
    }

    /**
     * Копия-владелец закреплённой детали с объявлениями каталога и
     * клаузами фазы; пусто — детали с таким идентификатором в копиях нет.
     *
     * <p>Отбор идёт от ДЕТАЛИ, а не от пары «счёт, инструмент»: сделка
     * ведётся по той копии, чью деталь она закрепила, и активная копия
     * пары к моменту прохода может быть уже другой — читать привязки у
     * неё значило бы оценивать условия закреплённой детали по чужим
     * объявлениям.
     */
    @Transactional(readOnly = true)
    public Optional<Strategy> findOwnerOfDetailWithSettings(Long detailId) {
        return repository.findOwnerOfDetailWithSettings(detailId).map(mapper::persistenceToDomainWithSettings);
    }

    /** Копия с объявлениями каталога и без дерева деталей; пусто — копии нет. */
    @Transactional(readOnly = true)
    public Optional<Strategy> findWithSettings(Long id) {
        return repository.findByIdWithSettings(id).map(mapper::persistenceToDomainWithSettings);
    }

    /**
     * Идентичность определения, которому принадлежит закреплённая деталь —
     * поверх ПРОЕКЦИИ одного поля, а не загрузки детали с деревом
     * (.claude/rules/codestyle.md §«Выборка данных»).
     *
     * <p>Читатель — писатель события сделки, у которого контекста прохода
     * нет: у него на руках только ключ закреплённой детали, а событию нужна
     * идентичность её владельца.
     *
     * <p><b>Отсутствие строки — нарушенный инвариант, а не пустое
     * значение:</b> деталь принадлежит копии по построению, и «детали нет»
     * от «определения у сделки нет» отличает вызывающий — он и не зовёт
     * резолв, когда деталь не закреплена.
     */
    @Transactional(readOnly = true)
    public String getRequiredStrategyInternalIdByDetailId(Long detailId) {
        return repository.findStrategyInternalIdByDetailId(detailId)
                .orElseThrow(() -> new IllegalStateException(
                        "Strategy copy of the pinned detail not found: detail " + detailId));
    }

    /**
     * Переставляет статус копии по идентичности её определения.
     *
     * <p><b>Транзакцию открывает вызывающий.</b> Статус копии и отметка
     * обработки события ложатся одним ходом: отметка без следствия
     * потеряла бы событие навсегда.
     *
     * <p><b>Дерево при этом не трогается</b> — определение неизменяемо, и
     * переписывание оборвало бы закрепление детали у живой сделки
     * (docs/models/domain/aggregate/Strategy.md §«Что у копии неизменяемо,
     * а что состояние»).
     */
    public void applyStatus(String internalId, Strategy.Status status) {
        StrategyEntity entity = repository.findByInternalId(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy copy not found: " + internalId));
        entity.setStatus(status.name());
        repository.save(entity);
    }

    /**
     * Привязывает объявление индикатора к идентичности вычисления —
     * точечно и write-once. Охрана делегируется запросу как есть:
     * оставленная вызывающему, она держалась бы ровно до второго
     * вызывающего.
     */
    @Transactional
    public void bindIndicatorComputation(Long settingId, String configInternalId) {
        indicatorSettingRepository.bindComputation(settingId, configInternalId);
    }

    /** То же для объявления структуры рынка. */
    @Transactional
    public void bindMarketStructureComputation(Long settingId, String configInternalId) {
        marketStructureSettingRepository.bindComputation(settingId, configInternalId);
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
