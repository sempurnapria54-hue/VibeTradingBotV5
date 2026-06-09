package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Канонизация params расчёта рыночных данных для идентичности
 * конфигурации в реестрах (indicator_configs / market_structure_configs).
 * Идентичность считаемого = тип + timeframe + canonical-params, где
 * canonical-params — стабильная (сортировка ключей, только непустые
 * значения) сериализация именно математических параметров: для
 * индикатора служебные {@code timeframe}/{@code warmup} базы исключаются
 * (timeframe — отдельная ось идентичности и колонка реестра; warmup —
 * loading/skip-хинт, не математический параметр расчёта). Ключи
 * сортируются явной пересборкой объекта (params плоские), без опоры на
 * feature-флаги маппера. См.
 * docs/decisions/market-data-result-identity-keying.md.
 */
@Component
public class MarketDataConfigWriter {

    private static final String TIMEFRAME_FIELD = "timeframe";
    private static final String WARMUP_FIELD = "warmup";

    private final ObjectMapper canonicalMapper;

    public MarketDataConfigWriter(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    /** Каноническая форма математических параметров индикатора (без timeframe/warmup базы). */
    public String canonicalIndicatorParams(IndicatorParams params) {
        ObjectNode node = canonicalMapper.valueToTree(params);
        node.remove(TIMEFRAME_FIELD);
        node.remove(WARMUP_FIELD);
        return writeSorted(node);
    }

    /** Каноническая форма параметров структуры рынка (timeframe у структуры — на настройке, не в params). */
    public String canonicalStructureParams(MarketStructureParams params) {
        return writeSorted(canonicalMapper.valueToTree(params));
    }

    /** Сериализация ObjectNode с детерминированным (отсортированным) порядком ключей. */
    private String writeSorted(ObjectNode node) {
        ObjectNode sorted = canonicalMapper.createObjectNode();
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.sort(String::compareTo);
        fieldNames.forEach(name -> sorted.set(name, node.get(name)));
        try {
            return canonicalMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Market data config canonicalization failed", e);
        }
    }
}
