package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Конвертация JSONB-навеса дерева Strategy: доменные value-объекты ↔
 * сериализованный JSON строк persistence-слоя (настройки рыночных
 * данных, params, условие шага, политика устаревания, вложенные
 * настройки действий). Пишутся только непустые значения; Duration —
 * ISO-8601; дискриминатор подтипа IndicatorParams в payload не
 * дублируется (его несёт indicatorType владельца, EXTERNAL_PROPERTY).
 * Методы подхватывает StrategyMapper (MapStruct uses) по парам типов.
 */
@Component
public class StrategyJsonConverter {

    private final ObjectMapper objectMapper;

    public StrategyJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
    }

    public String indicatorSettingsToJson(List<StrategyIndicatorSetting> settings) {
        return writeJson(settings);
    }

    public List<StrategyIndicatorSetting> jsonToIndicatorSettings(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String marketStructureSettingsToJson(List<StrategyMarketStructureSetting> settings) {
        return writeJson(settings);
    }

    public List<StrategyMarketStructureSetting> jsonToMarketStructureSettings(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String phaseRulesToJson(List<StrategyMarketPhaseRule> phaseRules) {
        return writeJson(phaseRules);
    }

    public List<StrategyMarketPhaseRule> jsonToPhaseRules(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String conditionToJson(StrategyCondition condition) {
        return writeJson(condition);
    }

    public StrategyCondition jsonToCondition(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String expiredSettingToJson(StrategyMarketDataExpiredSetting setting) {
        return writeJson(setting);
    }

    public StrategyMarketDataExpiredSetting jsonToExpiredSetting(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String placementToJson(StrategyPricePlacement placement) {
        return writeJson(placement);
    }

    public StrategyPricePlacement jsonToPlacement(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String attachedProtectionToJson(StrategyAttachedProtectionSettings settings) {
        return writeJson(settings);
    }

    public StrategyAttachedProtectionSettings jsonToAttachedProtection(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String stopLossSettingsToJson(StopLossSettings settings) {
        return writeJson(settings);
    }

    public StopLossSettings jsonToStopLossSettings(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    public String trailingSettingsToJson(TrailingSettings settings) {
        return writeJson(settings);
    }

    public TrailingSettings jsonToTrailingSettings(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    private String writeJson(Object value) {
        if (isNull(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Strategy JSONB serialization failed", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Strategy JSONB deserialization failed", e);
        }
    }
}
