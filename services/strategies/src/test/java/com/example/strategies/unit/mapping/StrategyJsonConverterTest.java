package com.example.strategies.unit.mapping;

import com.example.strategies.mapping.StrategyJsonConverter;
import com.example.strategies.persistence.model.StrategyIndicatorSettingEntity;
import com.example.testsupport.StrategyOverlayCopyContract;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Копия навеса дерева стратегии в дереве сервиса стратегий: ожидания живут у
 * формы ({@link StrategyOverlayCopyContract}), здесь — только порты к своей
 * копии и своей строке-владельцу.
 *
 * <p>Кейсы — `U4`, `U5`, `U6`, `U10.1`, `U10.2`
 * (.claude/tests/cases/jsonb-overlay-roundtrip.md).
 */
class StrategyJsonConverterTest extends StrategyOverlayCopyContract {

    private final StrategyJsonConverter converter = new StrategyJsonConverter(beanAssemblyMapper());

    @Override
    protected String writeIndicatorParams(IndicatorParams params) {
        return converter.indicatorParamsToJson(params);
    }

    @Override
    protected IndicatorParams readIndicatorParams(String indicatorType, String paramsJson) {
        StrategyIndicatorSettingEntity owner = new StrategyIndicatorSettingEntity();
        owner.setIndicatorType(indicatorType);
        owner.setParams(paramsJson);
        return converter.indicatorParamsFromEntity(owner);
    }

    @Override
    protected IndicatorParams readIndicatorParamsWithoutOwner() {
        return converter.indicatorParamsFromEntity(null);
    }

    @Override
    protected String writeMarketStructureParams(MarketStructureParams params) {
        return converter.marketStructureParamsToJson(params);
    }

    @Override
    protected MarketStructureParams readMarketStructureParams(String json) {
        return converter.jsonToMarketStructureParams(json);
    }

    @Override
    protected String writePhaseRules(List<StrategyMarketPhaseRule> rules) {
        return converter.phaseRulesToJson(rules);
    }

    @Override
    protected List<StrategyMarketPhaseRule> readPhaseRules(String json) {
        return converter.jsonToPhaseRules(json);
    }

    @Override
    protected String writeCondition(StrategyCondition condition) {
        return converter.conditionToJson(condition);
    }

    @Override
    protected StrategyCondition readCondition(String json) {
        return converter.jsonToCondition(json);
    }

    @Override
    protected String writeExpiredSetting(StrategyMarketDataExpiredSetting setting) {
        return converter.expiredSettingToJson(setting);
    }

    @Override
    protected StrategyMarketDataExpiredSetting readExpiredSetting(String json) {
        return converter.jsonToExpiredSetting(json);
    }

    @Override
    protected String writePlacement(StrategyPricePlacement placement) {
        return converter.placementToJson(placement);
    }

    @Override
    protected StrategyPricePlacement readPlacement(String json) {
        return converter.jsonToPlacement(json);
    }

    @Override
    protected String writeAttachedProtection(StrategyAttachedProtectionSettings settings) {
        return converter.attachedProtectionToJson(settings);
    }

    @Override
    protected StrategyAttachedProtectionSettings readAttachedProtection(String json) {
        return converter.jsonToAttachedProtection(json);
    }

    @Override
    protected String writeStopLossSettings(StopLossSettings settings) {
        return converter.stopLossSettingsToJson(settings);
    }

    @Override
    protected StopLossSettings readStopLossSettings(String json) {
        return converter.jsonToStopLossSettings(json);
    }

    @Override
    protected String writeTrailingSettings(TrailingSettings settings) {
        return converter.trailingSettingsToJson(settings);
    }

    @Override
    protected TrailingSettings readTrailingSettings(String json) {
        return converter.jsonToTrailingSettings(json);
    }

    @Override
    protected String writePlacementOn(ObjectMapper source, StrategyPricePlacement placement) {
        return new StrategyJsonConverter(source).placementToJson(placement);
    }
}
