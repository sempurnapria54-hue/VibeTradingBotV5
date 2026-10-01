package com.example.tradingcore.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Конвертация JSONB-навеса справочных правил инструмента: доменные
 * {@link InstrumentExternalRules} ↔ сериализованный JSON строки-владельца
 * (колонка external_rules проекции каталога).
 *
 * <p><b>Числовой идентификатор инструмента в навес не едет, и это не
 * косметика.</b> В ответе владельца каталога он называет строку ЕГО базы;
 * записанный в проекцию, он стал бы ключом чужой базы внутри нашей — ровно
 * то, что запрещено границей сервиса
 * (docs/models/domain/core/Instrument.md §«Проекция у торгового ядра»).
 * Своя строка свой идентификатор и так знает.
 *
 * <p><b>Ставка комиссии в навес не едет тоже</b>, и правило изъятия у обеих
 * копий конвертера одно (docs/models/domain/other/InstrumentExternalRules.md
 * §«Ставка комиссии»). Ставку на объект наливает хранилищный слой при
 * чтении из строки комиссионной группы счёта; записанная обратно, она стала
 * бы вторым носителем значения, расходящимся со сменой тира.
 *
 * <p>Знание о хранении выражено примесью, а не аннотацией на доменной
 * модели: форма лежит в общей библиотеке и о том, кто и как её хранит,
 * знать не должна. Предикат торгуемости примесью не изымается: он не поле,
 * а вопрос к модели, и изъят на ней самой — у всякого сериализатора разом
 * (.claude/rules/codestyle.md §«Предикат формы, пересекающей сериализацию,
 * не является её свойством»).
 *
 * <p><b>Маппер — копия общего бина, и копия пинит сама ДВЕ оси</b>
 * (docs/rules/persistence-representation.md §«Состав ключей строки
 * навеса»): политику включения — пишутся только непустые значения — и
 * терпимость к неизвестному свойству при чтении своей строки — строка,
 * записанная прежней редакцией формы, читается, лишний ключ отбрасывается.
 * Ни одна ось не наследуется умолчанием бина: настройка сериализатора
 * сервиса сдвигала бы форму колонки либо делала её нечитаемой целиком.
 */
@Component
public class InstrumentExternalRulesJsonConverter {

    private final ObjectMapper objectMapper;

    public InstrumentExternalRulesJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
