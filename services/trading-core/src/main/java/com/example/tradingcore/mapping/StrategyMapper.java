package com.example.tradingcore.mapping;

import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingcore.persistence.model.StrategyActionEntity;
import com.example.tradingcore.persistence.model.StrategyAlgoOrderActionEntity;
import com.example.tradingcore.persistence.model.StrategyDetailEntity;
import com.example.tradingcore.persistence.model.StrategyEntity;
import com.example.tradingcore.persistence.model.StrategyIndicatorSettingEntity;
import com.example.tradingcore.persistence.model.StrategyMarketPhaseSettingEntity;
import com.example.tradingcore.persistence.model.StrategyMarketStructureSettingEntity;
import com.example.tradingcore.persistence.model.StrategyOrderActionEntity;
import com.example.tradingcore.persistence.model.StrategyPositionActionEntity;
import com.example.tradingcore.persistence.model.StrategyStepEntity;
import com.example.tradingcore.persistence.model.StrategyTrancheEntity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mapstruct.AfterMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

/**
 * Маппинг копии дерева стратегии domain ↔ persistence.
 *
 * <p>Формы приёма определения здесь нет: её принимает поверхность ядра
 * своим слоем, а эта граница переносит дерево между доменом и строками
 * базы.
 *
 * <p>Полиморфные ветви видов действий — подклассовым маппингом; навес
 * JSONB — через {@link StrategyJsonConverter} (подбор по парам типов);
 * карты шагов ↔ плоские строки — методами по умолчанию.
 *
 * <p><b>Порядок восстанавливается, а не хранится:</b> действия читаются в
 * порядке идентификаторов (он же порядок вставки), шаги — по индексу
 * внутри своего ключа группировки. Обратные ссылки дерева выставляет
 * связывание после маппинга; отложенный FK цели действия резолвит
 * {@code StrategyDataService} при сохранении.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION,
        uses = StrategyJsonConverter.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StrategyMapper {

    // ===== domain -> persistence =====

    StrategyEntity domainToPersistence(Strategy strategy);

    StrategyMarketPhaseSettingEntity domainToPersistence(StrategyMarketPhaseSetting setting);

    @Mapping(target = "params", source = "params", qualifiedByName = "indicatorParamsToJson")
    StrategyIndicatorSettingEntity domainToPersistence(StrategyIndicatorSetting setting);

    StrategyMarketStructureSettingEntity domainToPersistence(StrategyMarketStructureSetting setting);

    @Mapping(target = "steps", source = "stepsByStatus")
    StrategyDetailEntity domainToPersistence(StrategyDetail detail);

    StrategyStepEntity domainToPersistence(StrategyStep step);

    @Mapping(target = "steps", source = "stepsByStatus")
    StrategyTrancheEntity domainToPersistence(StrategyTranche tranche);

    @SubclassMapping(source = StrategyOrderAction.class, target = StrategyOrderActionEntity.class)
    @SubclassMapping(source = StrategyAlgoOrderAction.class, target = StrategyAlgoOrderActionEntity.class)
    @SubclassMapping(source = StrategyPositionAction.class, target = StrategyPositionActionEntity.class)
    StrategyActionEntity domainToPersistence(StrategyAction action);

    /**
     * Карта шагов ТРАНША в плоские строки: статус транша — ключ карты,
     * индекс — позиция в списке. Множество с сохранением порядка вставки:
     * идентификаторы присваиваются в порядке авторинга.
     */
    default Set<StrategyStepEntity> trancheStepsDomainToPersistence(
            Map<DealTranche.Status, List<StrategyStep>> steps) {
        if (isNull(steps)) {
            return null;
        }
        Set<StrategyStepEntity> result = new LinkedHashSet<>();
        steps.forEach((status, list) -> {
            for (int index = 0; index < list.size(); index++) {
                StrategyStepEntity entity = domainToPersistence(list.get(index));
                entity.setTrancheStatus(status.name());
                entity.setStepIndex(index);
                result.add(entity);
            }
        });
        return result;
    }

    /**
     * Карта шагов УРОВНЯ СДЕЛКИ в плоские строки: ключ группировки свой,
     * статус сделки. Вторая колонка, а не та же самая: строка с обоими
     * ключами читалась бы двумя уровнями сразу, и ограничение схемы её
     * отвергает.
     */
    default Set<StrategyStepEntity> dealLevelStepsDomainToPersistence(
            Map<Deal.Status, List<StrategyStep>> steps) {
        if (isNull(steps)) {
            return null;
        }
        Set<StrategyStepEntity> result = new LinkedHashSet<>();
        steps.forEach((status, list) -> {
            for (int index = 0; index < list.size(); index++) {
                StrategyStepEntity entity = domainToPersistence(list.get(index));
                entity.setDealStatus(status.name());
                entity.setStepIndex(index);
                result.add(entity);
            }
        });
        return result;
    }

    /** Пакет действий в множество с порядком вставки: он и есть порядок авторинга. */
    default Set<StrategyActionEntity> actionsDomainToPersistence(List<StrategyAction> actions) {
        if (isNull(actions)) {
            return null;
        }
        return actions.stream().map(this::domainToPersistence).collect(toCollection(LinkedHashSet::new));
    }

    /**
     * Обратные ссылки дерева: настройка фазы, каталог, детали, объявления
     * траншей, шаги обоих уровней и действия знают родителей. Действие
     * ссылается на деталь с обоих уровней — денормализованный ключ
     * «деталь, ключ действия» один на всю деталь.
     */
    @AfterMapping
    default void wireTree(@MappingTarget StrategyEntity entity) {
        if (nonNull(entity.getMarketPhaseSetting())) {
            entity.getMarketPhaseSetting().setStrategy(entity);
        }
        if (nonNull(entity.getIndicatorSettings())) {
            entity.getIndicatorSettings().forEach(setting -> setting.setStrategy(entity));
        }
        if (nonNull(entity.getMarketStructureSettings())) {
            entity.getMarketStructureSettings().forEach(setting -> setting.setStrategy(entity));
        }
        if (isNull(entity.getDetails())) {
            return;
        }
        entity.getDetails().forEach(detail -> {
            detail.setStrategy(entity);
            wireDealLevelSteps(detail);
            wireTranches(detail);
        });
    }

    /** Шаги узкой агрегатной поверхности висят на самой детали. */
    default void wireDealLevelSteps(StrategyDetailEntity detail) {
        if (isNull(detail.getSteps())) {
            return;
        }
        detail.getSteps().forEach(step -> {
            step.setDetail(detail);
            step.setTranche(null);
            wireActions(step, detail);
        });
    }

    /** Шаги объявления висят на транше; деталь они знают только через него. */
    default void wireTranches(StrategyDetailEntity detail) {
        if (isNull(detail.getTranches())) {
            return;
        }
        detail.getTranches().forEach(tranche -> {
            tranche.setDetail(detail);
            if (isNull(tranche.getSteps())) {
                return;
            }
            tranche.getSteps().forEach(step -> {
                step.setTranche(tranche);
                step.setDetail(null);
                wireActions(step, detail);
            });
        });
    }

    private void wireActions(StrategyStepEntity step, StrategyDetailEntity detail) {
        if (isNull(step.getActions())) {
            return;
        }
        step.getActions().forEach(action -> {
            action.setStep(step);
            action.setDetail(detail);
        });
    }

    // ===== persistence -> domain =====

    /** Строка в домен БЕЗ дерева: корневые операции по идентичности и статусу. */
    @Mapping(target = "marketPhaseSetting", ignore = true)
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "indicatorSettings", ignore = true)
    @Mapping(target = "marketStructureSettings", ignore = true)
    Strategy persistenceToDomain(StrategyEntity entity);

    /**
     * Строка в домен вместе с объявлениями каталога, но БЕЗ дерева
     * деталей: вход тика объявления потребности у владельца рыночных
     * данных. Отдельный метод, а не общий с деревом — иначе тик тянул бы
     * шаги и действия, которые потребности не выражают.
     */
    @Mapping(target = "details", ignore = true)
    Strategy persistenceToDomainWithSettings(StrategyEntity entity);

    /** Строка в домен ВМЕСТЕ с деревом; грузить его нужно одним запросом. */
    @Mapping(target = "details", source = "details", qualifiedByName = "detailsWithStableOrder")
    Strategy persistenceToDomainWithTree(StrategyEntity entity);

    StrategyMarketPhaseSetting persistenceToDomain(StrategyMarketPhaseSettingEntity entity);

    @Mapping(target = "params", source = ".", qualifiedByName = "indicatorParamsFromEntity")
    StrategyIndicatorSetting persistenceToDomain(StrategyIndicatorSettingEntity entity);

    StrategyMarketStructureSetting persistenceToDomain(StrategyMarketStructureSettingEntity entity);

    @Mapping(target = "stepsByStatus", source = "steps")
    StrategyDetail persistenceToDomain(StrategyDetailEntity entity);

    StrategyStep persistenceToDomain(StrategyStepEntity entity);

    @Mapping(target = "stepsByStatus", source = "steps")
    StrategyTranche persistenceToDomain(StrategyTrancheEntity entity);

    @SubclassMapping(source = StrategyOrderActionEntity.class, target = StrategyOrderAction.class)
    @SubclassMapping(source = StrategyAlgoOrderActionEntity.class, target = StrategyAlgoOrderAction.class)
    @SubclassMapping(source = StrategyPositionActionEntity.class, target = StrategyPositionAction.class)
    StrategyAction persistenceToDomain(StrategyActionEntity entity);

    /** Детали из базы в список, упорядоченный фазой: ответ стабилен между проходами. */
    @Named("detailsWithStableOrder")
    default List<StrategyDetail> detailsPersistenceToDomain(Set<StrategyDetailEntity> details) {
        if (isNull(details)) {
            return null;
        }
        return details.stream()
                .map(this::persistenceToDomain)
                .sorted(comparing(StrategyDetail::getMarketPhaseType))
                .collect(toList());
    }

    /** Строки шагов транша в карту: ключи в порядке статусной модели, списки по индексу. */
    default Map<DealTranche.Status, List<StrategyStep>> trancheStepsPersistenceToDomain(
            Set<StrategyStepEntity> steps) {
        if (isNull(steps)) {
            return null;
        }
        Map<DealTranche.Status, List<StrategyStepEntity>> grouped = new EnumMap<>(DealTranche.Status.class);
        steps.forEach(step -> grouped
                .computeIfAbsent(DealTranche.Status.valueOf(step.getTrancheStatus()), status -> new ArrayList<>())
                .add(step));
        Map<DealTranche.Status, List<StrategyStep>> result = new EnumMap<>(DealTranche.Status.class);
        grouped.forEach((status, list) -> result.put(status, list.stream()
                .sorted(comparing(StrategyStepEntity::getStepIndex))
                .map(this::persistenceToDomain)
                .collect(toList())));
        return result;
    }

    /** Строки шагов уровня сделки в карту в порядке статусной модели агрегата. */
    default Map<Deal.Status, List<StrategyStep>> dealLevelStepsPersistenceToDomain(
            Set<StrategyStepEntity> steps) {
        if (isNull(steps)) {
            return null;
        }
        Map<Deal.Status, List<StrategyStepEntity>> grouped = new EnumMap<>(Deal.Status.class);
        steps.forEach(step -> grouped
                .computeIfAbsent(Deal.Status.valueOf(step.getDealStatus()), status -> new ArrayList<>())
                .add(step));
        Map<Deal.Status, List<StrategyStep>> result = new EnumMap<>(Deal.Status.class);
        grouped.forEach((status, list) -> result.put(status, list.stream()
                .sorted(comparing(StrategyStepEntity::getStepIndex))
                .map(this::persistenceToDomain)
                .collect(toList())));
        return result;
    }

    /** Строки действий в пакет в порядке авторинга. */
    default List<StrategyAction> actionsPersistenceToDomain(Set<StrategyActionEntity> actions) {
        if (isNull(actions)) {
            return null;
        }
        return actions.stream()
                .sorted(comparing(StrategyActionEntity::getId))
                .map(this::persistenceToDomain)
                .collect(toList());
    }

    // ===== общие конвертации =====

    /** Строка ISO-8601 в длительность (форма хранения). */
    default Duration stringToDuration(String value) {
        return isNull(value) ? null : Duration.parse(value);
    }

    /** Длительность в строку ISO-8601 (форма хранения). */
    default String durationToString(Duration value) {
        return isNull(value) ? null : value.toString();
    }
}
