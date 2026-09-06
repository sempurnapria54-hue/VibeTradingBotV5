package com.example.tradingcore.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingcore.domain.command.RetryError;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Конвертация JSONB-навеса runtime-строк ядра: доменные value-объекты ↔
 * сериализованный JSON колонок (дерево условия отдельной условной заявки,
 * перечень идентификаторов порождённых ею заявок и последняя ошибка
 * строки исполнения).
 *
 * <p>Пишутся только непустые значения: пустое поле в навесе неотличимо от
 * ненаблюдённого, а колонка навеса читается как факт.
 *
 * <p>Методы подхватываются мапперами через {@code uses} по парам типов.
 */
@Component
public class RuntimeJsonConverter {

    private final ObjectMapper objectMapper;

    public RuntimeJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    /** Дерево условия в JSON навеса; пусто на входе — пусто на выходе. */
    public String conditionToJson(Condition condition) {
        if (isNull(condition)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(condition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Condition JSONB serialization failed", e);
        }
    }

    /** JSON навеса в дерево условия; пусто на входе — пусто на выходе. */
    public Condition jsonToCondition(String json) {
        if (isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Condition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Condition JSONB deserialization failed", e);
        }
    }

    /**
     * Последняя ошибка строки исполнения в JSON навеса.
     *
     * <p><b>Объектом, а не тремя колонками:</b> код, сообщение и
     * классификация описывают ОДИН факт, и разнести их значило бы завести
     * три носителя одной истины
     * (docs/components/RetryPolicyService.md).
     */
    public String retryErrorToJson(RetryError error) {
        if (isNull(error)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Retry error JSONB serialization failed", e);
        }
    }

    /** JSON навеса в последнюю ошибку строки исполнения. */
    public RetryError jsonToRetryError(String json) {
        if (isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RetryError.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Retry error JSONB deserialization failed", e);
        }
    }

    /** Перечень идентификаторов в JSON навеса. */
    public String stringListToJson(List<String> values) {
        if (isNull(values)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("String list JSONB serialization failed", e);
        }
    }

    /** JSON навеса в перечень идентификаторов. */
    public List<String> jsonToStringList(String json) {
        if (isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("String list JSONB deserialization failed", e);
        }
    }
}
