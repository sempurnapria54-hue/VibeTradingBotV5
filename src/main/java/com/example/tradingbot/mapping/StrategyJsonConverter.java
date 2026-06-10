package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EmaParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.ObvParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StochasticParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.persistence.model.strategy.StrategyIndicatorSettingEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Конвертация JSONB-навеса дерева Strategy: доменные value-объекты ↔
 * сериализованный JSON строк persistence-слоя (params настроек, клаузы
 * фазы, условие шага, политика устаревания, вложенные настройки действий).
 * Пишутся только непустые значения; Duration — ISO-8601. params настройки
 * — JSONB-колонка её строки без дублирования тега: подтип IndicatorParams
 * восстанавливается по {@code indicator_type} строки-владельца (трек D,
 * docs/rules/persistence-representation.md). Методы подхватывает
 * StrategyMapper (MapStruct uses) по парам типов / qualifiedByName.
 */
@Component
public class StrategyJsonConverter {

    private final ObjectMapper objectMapper;

    public StrategyJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
    }

    /** params индикатора → JSON (без тега типа — он в колонке indicator_type строки). */
    @Named("indicatorParamsToJson")
    public String indicatorParamsToJson(IndicatorParams params) {
        return writeJson(params);
    }

    /**
     * JSON params → подтип IndicatorParams по {@code indicator_type}
     * строки-владельца (дискриминатор на владельце, не в payload).
     */
    @Named("indicatorParamsFromEntity")
    public IndicatorParams indicatorParamsFromEntity(StrategyIndicatorSettingEntity entity) {
        if (isNull(entity) || isNull(entity.getParams()) || isNull(entity.getIndicatorType())) {
            return null;
        }
        return readJson(entity.getParams(), indicatorParamsClass(entity.getIndicatorType()));
    }

    public String marketStructureParamsToJson(MarketStructureParams params) {
        return writeJson(params);
    }

    public MarketStructureParams jsonToMarketStructureParams(String json) {
        return readJson(json, MarketStructureParams.class);
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

    private Class<? extends IndicatorParams> indicatorParamsClass(String indicatorType) {
        return switch (IndicatorValue.Type.valueOf(indicatorType)) {
            case ATR -> AtrParams.class;
            case EMA -> EmaParams.class;
            case RSI -> RsiParams.class;
            case MACD -> MacdParams.class;
            case STOCHASTIC -> StochasticParams.class;
            case BOLLINGER_BANDS -> BollingerBandsParams.class;
            case OBV -> ObvParams.class;
            case EFFICIENCY_RATIO -> EfficiencyRatioParams.class;
        };
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

    private <T> T readJson(String json, Class<T> type) {
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
