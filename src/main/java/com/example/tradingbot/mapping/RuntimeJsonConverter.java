package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.command.RetryError;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Конвертация JSONB-навеса runtime-сущностей command-слоя: доменные
 * value-объекты ↔ сериализованный JSON строк persistence-слоя (условие
 * algo-order, связанные ordni ids, runtime-target и last-error
 * DealActionState). Пишутся только непустые значения. Методы
 * подхватывают мапперы (MapStruct uses) по парам типов. Параллель —
 * {@link StrategyJsonConverter}.
 */
@Component
public class RuntimeJsonConverter {

    private final ObjectMapper objectMapper;

    public RuntimeJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public String conditionToJson(Condition condition) {
        return writeJson(condition);
    }

    public Condition jsonToCondition(String json) {
        return readJson(json, Condition.class);
    }

    public String runtimeTargetToJson(RuntimeTarget target) {
        return writeJson(target);
    }

    public RuntimeTarget jsonToRuntimeTarget(String json) {
        return readJson(json, RuntimeTarget.class);
    }

    public String retryErrorToJson(RetryError error) {
        return writeJson(error);
    }

    public RetryError jsonToRetryError(String json) {
        return readJson(json, RetryError.class);
    }

    public String stringListToJson(List<String> values) {
        return writeJson(values);
    }

    public List<String> jsonToStringList(String json) {
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
            throw new IllegalStateException("Runtime JSONB serialization failed", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Runtime JSONB deserialization failed", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Runtime JSONB deserialization failed", e);
        }
    }
}
