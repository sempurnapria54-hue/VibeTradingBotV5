package com.example.tradingbot.mapping;

import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import com.example.tradingbot.api.model.request.CreateStrategyApiRequest;
import com.example.tradingbot.api.model.response.StrategyApiResponse;
import com.example.tradingbot.api.model.strategy.AtrParamsApiModel;
import com.example.tradingbot.api.model.strategy.BollingerBandsParamsApiModel;
import com.example.tradingbot.api.model.strategy.EfficiencyRatioParamsApiModel;
import com.example.tradingbot.api.model.strategy.EmaParamsApiModel;
import com.example.tradingbot.api.model.strategy.IndicatorParamsApiModel;
import com.example.tradingbot.api.model.strategy.MacdParamsApiModel;
import com.example.tradingbot.api.model.strategy.ObvParamsApiModel;
import com.example.tradingbot.api.model.strategy.RsiParamsApiModel;
import com.example.tradingbot.api.model.strategy.StochasticParamsApiModel;
import com.example.tradingbot.api.model.strategy.StrategyActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyDetailApiModel;
import com.example.tradingbot.api.model.strategy.StrategyIndicatorSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketPhaseRuleApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketPhaseSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketStructureSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyOrderActionApiModel;
import com.example.tradingbot.api.model.strategy.StrategyStepApiModel;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EmaParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.ObvParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StochasticParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.persistence.model.strategy.StrategyActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyAlgoOrderActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyDetailEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyIndicatorSettingEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyMarketPhaseSettingEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyMarketStructureSettingEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyOrderActionEntity;
import com.example.tradingbot.persistence.model.strategy.StrategyStepEntity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

/**
 * Маппинг стратегии между слоями: api ↔ domain ↔ persistence.
 * Полиморфные ветви (виды действий, подтипы params) — через
 * SubclassMapping; JSONB-навес (настройки, params, условие, политика
 * устаревания, вложенные настройки действий) — через
 * {@link StrategyJsonConverter} (подбор по парам типов);
 * stepsByStatus ↔ плоские строки шагов (deal_status + step_index) —
 * default-методами. Наружу — internalId стратегии и
 * instrumentInternalId, не id из БД. Back-ссылки выставляет wiring
 * после маппинга; порядок действий кодируется порядком вставки
 * (LinkedHashSet → id ASC); self-FK target_action_id резолвит
 * StrategyDataService при сохранении.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION,
        uses = StrategyJsonConverter.class)
public interface StrategyMapper {

    // ===== api -> domain =====

    Strategy apiToDomain(CreateStrategyApiRequest request);

    StrategyMarketPhaseSetting apiToDomain(StrategyMarketPhaseSettingApiModel api);

    StrategyIndicatorSetting apiToDomain(StrategyIndicatorSettingApiModel api);

    StrategyMarketStructureSetting apiToDomain(StrategyMarketStructureSettingApiModel api);

    @SubclassMapping(source = AtrParamsApiModel.class, target = AtrParams.class)
    @SubclassMapping(source = EmaParamsApiModel.class, target = EmaParams.class)
    @SubclassMapping(source = RsiParamsApiModel.class, target = RsiParams.class)
    @SubclassMapping(source = MacdParamsApiModel.class, target = MacdParams.class)
    @SubclassMapping(source = ObvParamsApiModel.class, target = ObvParams.class)
    @SubclassMapping(source = StochasticParamsApiModel.class, target = StochasticParams.class)
    @SubclassMapping(source = BollingerBandsParamsApiModel.class, target = BollingerBandsParams.class)
    @SubclassMapping(source = EfficiencyRatioParamsApiModel.class, target = EfficiencyRatioParams.class)
    IndicatorParams apiToDomain(IndicatorParamsApiModel api);

    StrategyMarketPhaseRule apiToDomain(StrategyMarketPhaseRuleApiModel api);

    StrategyDetail apiToDomain(StrategyDetailApiModel api);

    StrategyStep apiToDomain(StrategyStepApiModel api);

    @SubclassMapping(source = StrategyOrderActionApiModel.class, target = StrategyOrderAction.class)
    @SubclassMapping(source = StrategyAlgoOrderActionApiModel.class, target = StrategyAlgoOrderAction.class)
    StrategyAction apiToDomain(StrategyActionApiModel api);

    /** stepsByStatus формы ввода → доменная map (порядок запроса сохраняется). */
    default Map<Deal.Status, List<StrategyStep>> stepsApiToDomain(Map<String, List<StrategyStepApiModel>> api) {
        if (isNull(api)) {
            return null;
        }
        Map<Deal.Status, List<StrategyStep>> result = new LinkedHashMap<>();
        api.forEach((status, steps) -> result.put(Deal.Status.valueOf(status),
                steps.stream().map(this::apiToDomain).collect(toList())));
        return result;
    }

    // ===== domain -> api =====

    @Mapping(target = "instrumentInternalId", source = "instrumentInternalId")
    StrategyApiResponse domainToApi(Strategy strategy, String instrumentInternalId);

    StrategyMarketPhaseSettingApiModel domainToApi(StrategyMarketPhaseSetting setting);

    StrategyIndicatorSettingApiModel domainToApi(StrategyIndicatorSetting setting);

    StrategyMarketStructureSettingApiModel domainToApi(StrategyMarketStructureSetting setting);

    @SubclassMapping(source = AtrParams.class, target = AtrParamsApiModel.class)
    @SubclassMapping(source = EmaParams.class, target = EmaParamsApiModel.class)
    @SubclassMapping(source = RsiParams.class, target = RsiParamsApiModel.class)
    @SubclassMapping(source = MacdParams.class, target = MacdParamsApiModel.class)
    @SubclassMapping(source = ObvParams.class, target = ObvParamsApiModel.class)
    @SubclassMapping(source = StochasticParams.class, target = StochasticParamsApiModel.class)
    @SubclassMapping(source = BollingerBandsParams.class, target = BollingerBandsParamsApiModel.class)
    @SubclassMapping(source = EfficiencyRatioParams.class, target = EfficiencyRatioParamsApiModel.class)
    IndicatorParamsApiModel domainToApi(IndicatorParams params);

    StrategyMarketPhaseRuleApiModel domainToApi(StrategyMarketPhaseRule rule);

    StrategyDetailApiModel domainToApi(StrategyDetail detail);

    StrategyStepApiModel domainToApi(StrategyStep step);

    @SubclassMapping(source = StrategyOrderAction.class, target = StrategyOrderActionApiModel.class)
    @SubclassMapping(source = StrategyAlgoOrderAction.class, target = StrategyAlgoOrderActionApiModel.class)
    StrategyActionApiModel domainToApi(StrategyAction action);

    /** Доменная map шагов → форма ответа (порядок ключей сохраняется). */
    default Map<String, List<StrategyStepApiModel>> stepsDomainToApi(Map<Deal.Status, List<StrategyStep>> steps) {
        if (isNull(steps)) {
            return null;
        }
        Map<String, List<StrategyStepApiModel>> result = new LinkedHashMap<>();
        steps.forEach((status, list) -> result.put(status.name(),
                list.stream().map(this::domainToApi).collect(toList())));
        return result;
    }

    // ===== domain -> persistence =====

    StrategyEntity domainToPersistence(Strategy strategy);

    StrategyMarketPhaseSettingEntity domainToPersistence(StrategyMarketPhaseSetting setting);

    @Mapping(target = "params", source = "params", qualifiedByName = "indicatorParamsToJson")
    StrategyIndicatorSettingEntity domainToPersistence(StrategyIndicatorSetting setting);

    StrategyMarketStructureSettingEntity domainToPersistence(StrategyMarketStructureSetting setting);

    @Mapping(target = "steps", source = "stepsByStatus")
    StrategyDetailEntity domainToPersistence(StrategyDetail detail);

    StrategyStepEntity domainToPersistence(StrategyStep step);

    @SubclassMapping(source = StrategyOrderAction.class, target = StrategyOrderActionEntity.class)
    @SubclassMapping(source = StrategyAlgoOrderAction.class, target = StrategyAlgoOrderActionEntity.class)
    StrategyActionEntity domainToPersistence(StrategyAction action);

    /**
     * Доменная map шагов → плоские строки: deal_status — ключ map,
     * step_index — позиция в списке. LinkedHashSet сохраняет порядок
     * вставки (id присваиваются в порядке авторинга).
     */
    default Set<StrategyStepEntity> stepsDomainToPersistence(Map<Deal.Status, List<StrategyStep>> steps) {
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

    /** Пакет действий → LinkedHashSet: порядок авторинга = порядок вставки строк. */
    default Set<StrategyActionEntity> actionsDomainToPersistence(List<StrategyAction> actions) {
        if (isNull(actions)) {
            return null;
        }
        return actions.stream().map(this::domainToPersistence).collect(toCollection(LinkedHashSet::new));
    }

    /** Back-ссылки дерева: настройка фазы, детали, шаги и действия знают родителей. */
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
            if (isNull(detail.getSteps())) {
                return;
            }
            detail.getSteps().forEach(step -> {
                step.setDetail(detail);
                if (isNull(step.getActions())) {
                    return;
                }
                step.getActions().forEach(action -> {
                    action.setStep(step);
                    action.setDetail(detail);
                });
            });
        });
    }

    // ===== persistence -> domain =====

    /** Entity → domain без дерева (корневые операции: статус, идентичность). */
    @Mapping(target = "marketPhaseSetting", ignore = true)
    @Mapping(target = "details", ignore = true)
    @Mapping(target = "indicatorSettings", ignore = true)
    @Mapping(target = "marketStructureSettings", ignore = true)
    Strategy persistenceToDomain(StrategyEntity entity);

    /**
     * Entity → domain ВМЕСТЕ с деревом — для выдачи/проверок полной
     * стратегии; грузить дерево нужно join fetch'ем.
     */
    @Mapping(target = "details", source = "details", qualifiedByName = "detailsWithStableOrder")
    Strategy persistenceToDomainWithTree(StrategyEntity entity);

    /**
     * Entity → domain только с настройками рыночных данных (для jobs
     * расчёта): фаза (phaseRules) и strategy-scope-настройки
     * индикаторов/структуры; детали и шаги не маппятся (трек D).
     */
    @Mapping(target = "details", ignore = true)
    Strategy persistenceToDomainWithSettings(StrategyEntity entity);

    StrategyMarketPhaseSetting persistenceToDomain(StrategyMarketPhaseSettingEntity entity);

    @Mapping(target = "params", source = ".", qualifiedByName = "indicatorParamsFromEntity")
    StrategyIndicatorSetting persistenceToDomain(StrategyIndicatorSettingEntity entity);

    StrategyMarketStructureSetting persistenceToDomain(StrategyMarketStructureSettingEntity entity);

    @Mapping(target = "stepsByStatus", source = "steps")
    StrategyDetail persistenceToDomain(StrategyDetailEntity entity);

    StrategyStep persistenceToDomain(StrategyStepEntity entity);

    @SubclassMapping(source = StrategyOrderActionEntity.class, target = StrategyOrderAction.class)
    @SubclassMapping(source = StrategyAlgoOrderActionEntity.class, target = StrategyAlgoOrderAction.class)
    StrategyAction persistenceToDomain(StrategyActionEntity entity);

    /** Детали из БД → список, отсортированный по фазе (стабильный порядок ответа). */
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

    /** Плоские строки шагов → доменная map (ключи — порядок Deal.Status, списки — по step_index). */
    default Map<Deal.Status, List<StrategyStep>> stepsPersistenceToDomain(Set<StrategyStepEntity> steps) {
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

    /** Строки действий → пакет в порядке авторинга (id ASC). */
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

    /** ISO-8601 строка → Duration (формы ввода/хранения). */
    default Duration stringToDuration(String value) {
        return isNull(value) ? null : Duration.parse(value);
    }

    /** Duration → ISO-8601 строка (формы вывода/хранения). */
    default String durationToString(Duration value) {
        return isNull(value) ? null : value.toString();
    }
}
