package com.example.tradingbot.persistence.converter;

import com.example.tradingbot.persistence.model.algo_order.Condition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import static java.util.Objects.isNull;

@Converter
public class AlgoOrderConditionJsonbConverter implements AttributeConverter<Condition, String> {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    @Override
    public String convertToDatabaseColumn(Condition attribute) {
        if (isNull(attribute)) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize Condition to JSON", e);
        }
    }

    @Override
    public Condition convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(dbData, Condition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize Condition from JSON", e);
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}