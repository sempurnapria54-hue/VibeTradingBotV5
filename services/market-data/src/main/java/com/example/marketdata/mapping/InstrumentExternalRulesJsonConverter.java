package com.example.marketdata.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Конвертация JSONB-навеса внешних правил инструмента: доменные
 * {@link InstrumentExternalRules} ↔ сериализованный JSON строки-владельца
 * (колонка external_rules таблицы instruments). Пишутся только непустые
 * значения; доменные перечни — строкой. Один актуальный набор правил на
 * инструмент.
 *
 * <p><b>Что в навес не пишется, объявляет ЭТОТ слой, а не форма.</b>
 * Ставка комиссии принадлежит комиссионной группе счёта, а не справочнику
 * инструмента (docs/models/domain/other/TradeFeeRate.md), и копия на
 * инструменте разошлась бы со сменой тира. Прежде это выражалось
 * аннотацией на самой доменной модели — то есть форма знала о своём
 * хранении, и общая библиотека из-за одной аннотации объявляла
 * зависимость от Jackson. Здесь то же правило выражено примесью:
 * знание о хранении осталось в хранилище.
 */
@Component
public class InstrumentExternalRulesJsonConverter {

    private final ObjectMapper objectMapper;

    public InstrumentExternalRulesJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .addMixIn(InstrumentExternalRules.class, InstrumentExternalRulesNavelMixin.class);
    }

    /** Доменные правила в JSON навеса; пусто на входе — пусто на выходе. */
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

    /** JSON навеса в доменные правила; пусто на входе — пусто на выходе. */
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

    /**
     * Примесь хранилищного слоя: поля, которые в навес не едут.
     *
     * <p>Приватный вложенный тип — у примеси нет потребителей вне этого
     * конвертера, и выносить её отдельным файлом значило бы объявить
     * общей то, что общим не является.
     */
    private abstract static class InstrumentExternalRulesNavelMixin {

        @JsonIgnore
        abstract String getExternalTakerFeeRate();
    }
}
