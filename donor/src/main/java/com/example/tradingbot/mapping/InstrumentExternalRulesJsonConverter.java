package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Конвертация JSONB-навеса внешних правил инструмента: доменный
 * {@link InstrumentExternalRules} ↔ сериализованный JSON строки-владельца
 * (колонка external_rules таблицы instruments). Пишутся только непустые
 * значения; доменные enum'ы — строкой ({@code name()}). Один актуальный
 * набор правил на инструмент.
 */
@Component
public class InstrumentExternalRulesJsonConverter {

    private final ObjectMapper objectMapper;

    public InstrumentExternalRulesJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    /** Доменные правила → JSON для навеса; {@code null} → {@code null}. */
    public String rulesToJson(InstrumentExternalRules rules) {
        if (isNull(rules)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("InstrumentExternalRules JSONB serialization failed", e);
        }
    }

    /** JSON навеса → доменные правила; {@code null} → {@code null}. */
    public InstrumentExternalRules jsonToRules(String json) {
        if (isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InstrumentExternalRules.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("InstrumentExternalRules JSONB deserialization failed", e);
        }
    }
}
