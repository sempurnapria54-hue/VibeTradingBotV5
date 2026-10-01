package com.example.marketdata.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.trade.market_snapshot.OrderBookLevel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Конвертация уровней книги заявок в JSONB-навес строки среза и обратно.
 *
 * <p>Уровни живут навесом, а не своей таблицей: FK на них ниоткуда не
 * ведёт, а нормализация дала бы сорок строк на срез вместо одной — на
 * проходе раз в минуту по всему листингу это два порядка объёма ряда,
 * который не чистится (docs/models/domain/other/MarketOrderBook.md
 * §Персистентность).
 *
 * <p>Маппер берётся <b>копией</b> общего бина, и копия пинит сама ДВЕ оси
 * (docs/rules/persistence-representation.md §«Состав ключей строки
 * навеса»). Политика непустых полей: пустое поле уровня в строку не
 * пишется — лишний ключ платился бы объёмом на каждом уровне каждого среза.
 * Терпимость к неизвестному свойству при чтении своей строки: срез,
 * записанный прежней редакцией формы, читается, лишний ключ отбрасывается.
 * Ни одна ось не наследуется умолчанием бина: настройка сериализатора
 * сервиса сдвигала бы форму колонки либо делала её нечитаемой целиком.
 */
@Component
public class OrderBookLevelJsonConverter {

    private static final TypeReference<List<OrderBookLevel>> LEVEL_LIST = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public OrderBookLevelJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Уровни в JSON навеса; пусто на входе — пустой массив, а не пустота. */
    public String levelsToJson(List<OrderBookLevel> levels) {
        try {
            return objectMapper.writeValueAsString(isNull(levels) ? List.of() : levels);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Order book levels serialization failed", e);
        }
    }

    /** JSON навеса в уровни; пусто на входе — пустой список. */
    public List<OrderBookLevel> jsonToLevels(String json) {
        if (isNull(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LEVEL_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Order book levels deserialization failed", e);
        }
    }
}
