package com.example.strategies.mapping;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.response.StrategyApiResponse;
import com.example.strategies.api.model.strategy.AtrParamsApiModel;
import com.example.strategies.api.model.strategy.BollingerBandsParamsApiModel;
import com.example.strategies.api.model.strategy.EfficiencyRatioParamsApiModel;
import com.example.strategies.api.model.strategy.EmaParamsApiModel;
import com.example.strategies.api.model.strategy.IndicatorParamsApiModel;
import com.example.strategies.api.model.strategy.MacdParamsApiModel;
import com.example.strategies.api.model.strategy.ObvParamsApiModel;
import com.example.strategies.api.model.strategy.RsiParamsApiModel;
import com.example.strategies.api.model.strategy.StochasticParamsApiModel;
import com.example.strategies.api.model.strategy.StrategyActionApiModel;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyIndicatorSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketPhaseRuleApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketPhaseSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketStructureSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyPositionActionApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

/**
 * Маппинг определения стратегии api ↔ domain.
 *
 * <p><b>Тенант через эту границу не переносится:</b> тела команды он не
 * пересекает вовсе, а проставляется приёмником из контекста вызова
 * (docs/models/domain/aggregate/Strategy.md §«У каждого поля контекста
 * назван писатель и момент»). Статус телом создания тоже не приходит —
 * его ставит система.
 *
 * <p>Полиморфные ветви (виды действий, подтипы параметров индикатора) —
 * подклассовым маппингом; {@code stepsByStatus} ↔ форма ввода с ключом-
 * строкой — методами по умолчанию: уровень объявления читается тем, кто
 * несёт шаг, и разные ключи двух уровней — это он и есть.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StrategyApiMapper {

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

    StrategyTranche apiToDomain(StrategyTrancheApiModel api);

    @SubclassMapping(source = StrategyOrderActionApiModel.class, target = StrategyOrderAction.class)
    @SubclassMapping(source = StrategyAlgoOrderActionApiModel.class, target = StrategyAlgoOrderAction.class)
    @SubclassMapping(source = StrategyPositionActionApiModel.class, target = StrategyPositionAction.class)
    StrategyAction apiToDomain(StrategyActionApiModel api);

    /**
     * Шаги ТРАНША формы ввода → доменная map (порядок запроса
     * сохраняется). Ключ разбирается как статус транша.
     */
    default Map<DealTranche.Status, List<StrategyStep>> trancheStepsApiToDomain(
            Map<String, List<StrategyStepApiModel>> api) {
        if (isNull(api)) {
            return null;
        }
        Map<DealTranche.Status, List<StrategyStep>> result = new LinkedHashMap<>();
        api.forEach((status, steps) -> result.put(DealTranche.Status.valueOf(status),
                steps.stream().map(this::apiToDomain).collect(toList())));
        return result;
    }

    /**
     * Шаги узкой агрегатной поверхности формы ввода → доменная map. Ключ
     * разбирается как статус СДЕЛКИ: уровень объявления читается тем,
     * кто несёт шаг, и разные ключи двух уровней — это он и есть.
     */
    default Map<Deal.Status, List<StrategyStep>> dealLevelStepsApiToDomain(
            Map<String, List<StrategyStepApiModel>> api) {
        if (isNull(api)) {
            return null;
        }
        Map<Deal.Status, List<StrategyStep>> result = new LinkedHashMap<>();
        api.forEach((status, steps) -> result.put(Deal.Status.valueOf(status),
                steps.stream().map(this::apiToDomain).collect(toList())));
        return result;
    }

    // ===== domain -> api =====

    StrategyApiResponse domainToApi(Strategy strategy);

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

    StrategyTrancheApiModel domainToApi(StrategyTranche tranche);

    @SubclassMapping(source = StrategyOrderAction.class, target = StrategyOrderActionApiModel.class)
    @SubclassMapping(source = StrategyAlgoOrderAction.class, target = StrategyAlgoOrderActionApiModel.class)
    @SubclassMapping(source = StrategyPositionAction.class, target = StrategyPositionActionApiModel.class)
    StrategyActionApiModel domainToApi(StrategyAction action);

    /** Доменная map шагов транша → форма ответа (порядок ключей сохраняется). */
    default Map<String, List<StrategyStepApiModel>> trancheStepsDomainToApi(
            Map<DealTranche.Status, List<StrategyStep>> steps) {
        if (isNull(steps)) {
            return null;
        }
        Map<String, List<StrategyStepApiModel>> result = new LinkedHashMap<>();
        steps.forEach((status, list) -> result.put(status.name(),
                list.stream().map(this::domainToApi).collect(toList())));
        return result;
    }

    /** Доменная map шагов уровня сделки → форма ответа. */
    default Map<String, List<StrategyStepApiModel>> dealLevelStepsDomainToApi(
            Map<Deal.Status, List<StrategyStep>> steps) {
        if (isNull(steps)) {
            return null;
        }
        Map<String, List<StrategyStepApiModel>> result = new LinkedHashMap<>();
        steps.forEach((status, list) -> result.put(status.name(),
                list.stream().map(this::domainToApi).collect(toList())));
        return result;
    }
}
