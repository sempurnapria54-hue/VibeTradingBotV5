package com.example.tests.e2e;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Разбор тел ответов, содержимого записей и колонок JSONB.
 *
 * <p><b>Числа — десятичными, а не двоичными:</b> кейс, сверяющий число
 * провода с числом базы, иначе сравнивал бы округления (ловушка TC-097
 * скилла кода тестов).
 */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private Json() {
    }

    /** Дерево текста JSON. */
    public static JsonNode tree(String text) {
        return MAPPER.readTree(text);
    }

    /** Объект JSON картой полей. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        return MAPPER.readValue(text, Map.class);
    }
}
