package com.example.tradingbot.persistence.converter;

import com.example.tradingbot.persistence.model.strategy.StrategyActionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;
import java.util.Objects;

@Converter
public class StrategyActionListJsonbConverter implements AttributeConverter<List<StrategyActionEntity>, String> {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<StrategyActionEntity> attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize StrategyActionEntity list to JSON", exception);
        }
    }

    @Override
    public List<StrategyActionEntity> convertToEntityAttribute(String dbData) {
        if (Objects.isNull(dbData) || dbData.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(dbData, new TypeReference<List<StrategyActionEntity>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize StrategyActionEntity list from JSON", exception);
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
