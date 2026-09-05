package com.example.marketdata.mapping;

import static java.util.Objects.isNull;

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
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

/**
 * Конвертация параметров вычисления: форма параметров ↔ JSON строки
 * реестра идентичностей плюс <b>каноническая</b> форма, по которой
 * идентичность сравнивается.
 *
 * <p><b>Каноническая форма — не то же самое, что хранимая.</b> Хранимая
 * отвечает на вопрос «из чего восстановить объект параметров»;
 * каноническая — на вопрос «та же это идентичность или другая». Вторая
 * обязана быть устойчива к порядку ключей: иначе одна и та же
 * «ATR(14) на 1H», записанная двумя порядками полей, завела бы в реестре
 * две строки, и значение считалось бы дважды — ровно то, ради чего реестр
 * и существует (docs/models/domain/other/IndicatorValue.md §«Ключевание —
 * идентичностью вычисления»).
 *
 * <p><b>Тег подтипа в payload не дублируется:</b> подтип параметров
 * восстанавливается по типу индикатора строки-владельца
 * (docs/rules/persistence-representation.md).
 */
@Component
public class ComputationParamsJsonConverter {

    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;

    public ComputationParamsJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        this.canonicalMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    /** Параметры в JSON строки реестра. */
    public String paramsToJson(Object params) {
        return writeJson(objectMapper, params);
    }

    /** Параметры в каноническую форму сравнения идентичности. */
    public String paramsToCanonical(Object params) {
        return writeJson(canonicalMapper, params);
    }

    /** JSON строки реестра в параметры индикатора по типу строки-владельца. */
    public IndicatorParams jsonToIndicatorParams(String json, IndicatorValue.Type indicatorType) {
        if (isNull(json) || isNull(indicatorType)) {
            return null;
        }
        return readJson(json, indicatorParamsClass(indicatorType));
    }

    /** JSON строки реестра в параметры структуры рынка. */
    public MarketStructureParams jsonToMarketStructureParams(String json) {
        if (isNull(json)) {
            return null;
        }
        return readJson(json, MarketStructureParams.class);
    }

    /**
     * Сырая форма запроса в параметры индикатора по заявленному типу.
     *
     * <p>Разбор идёт ПО ТИПУ строки-владельца, а не по тегу внутри тела:
     * тег в теле был бы вторым носителем той же истины и разошёлся бы с
     * первым (docs/rules/persistence-representation.md).
     */
    public IndicatorParams toIndicatorParams(Object raw, IndicatorValue.Type indicatorType) {
        if (isNull(raw) || isNull(indicatorType)) {
            return null;
        }
        return convert(raw, indicatorParamsClass(indicatorType));
    }

    /** Сырая форма запроса в параметры структуры рынка. */
    public MarketStructureParams toMarketStructureParams(Object raw) {
        if (isNull(raw)) {
            return null;
        }
        return convert(raw, MarketStructureParams.class);
    }

    private <T> T convert(Object raw, Class<T> type) {
        try {
            return objectMapper.convertValue(raw, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Computation params do not match declared type: " + type.getSimpleName(), e);
        }
    }

    private Class<? extends IndicatorParams> indicatorParamsClass(IndicatorValue.Type indicatorType) {
        return switch (indicatorType) {
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

    private String writeJson(ObjectMapper mapper, Object value) {
        if (isNull(value)) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Computation params serialization failed", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Computation params deserialization failed", e);
        }
    }
}
