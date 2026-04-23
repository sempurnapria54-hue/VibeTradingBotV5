package com.example.tradingbot.persistence.converter;

import com.example.tradingbot.persistence.model.strategy.StrategyConditionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter
public class StrategyConditionJsonbConverter implements AttributeConverter<StrategyConditionEntity, String> {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    @Override
    public String convertToDatabaseColumn(StrategyConditionEntity attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize StrategyConditionEntity to JSON", exception);
        }
    }

    @Override
    public StrategyConditionEntity convertToEntityAttribute(String dbData) {
        if (Objects.isNull(dbData) || dbData.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(dbData, StrategyConditionEntity.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize StrategyConditionEntity from JSON", exception);
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
