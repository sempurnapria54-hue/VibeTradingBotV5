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
 * <p><b>Из навеса изымаются два поля, и правило у обеих копий конвертера
 * одно</b> (docs/models/domain/other/InstrumentExternalRules.md
 * §«Ставка комиссии»). Ставка комиссии принадлежит комиссионной группе
 * счёта, а не справочнику инструмента
 * (docs/models/domain/other/TradeFeeRate.md), и копия на инструменте
 * разошлась бы со сменой тира. Идентификатор инструмента-владельца строка
 * и так знает, а в проекции ядра он был бы ключом чужой базы внутри нашей
 * (docs/models/domain/core/Instrument.md §«Проекция у торгового ядра»).
 *
 * <p>Знание о хранении выражено примесью, а не аннотацией на доменной
 * модели: форма лежит в общей библиотеке и о том, кто и как её хранит,
 * знать не должна.
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
        abstract Long getInstrumentId();

        @JsonIgnore
        abstract String getExternalTakerFeeRate();
    }
}
