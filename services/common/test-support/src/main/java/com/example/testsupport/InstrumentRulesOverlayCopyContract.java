package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Кейсы навеса справочных правил инструмента: группа `U7` и клетка `U10.3`
 * документа `.claude/tests/cases/jsonb-overlay-roundtrip.md`.
 *
 * <p><b>Копии здесь расходятся НАМЕРЕННО и по существу.</b>
 * {@code InstrumentExternalRulesJsonConverter} живёт двумя экземплярами, и
 * примесь у них изымает РАЗНОЕ: копия ядра — идентификатор владельца, копия
 * рыночных данных — ставку комиссии. Это не расхождение реализации одной
 * формы, а два разных правила, объявленных одинаково названными классами, —
 * поэтому изъятое и удержанное объявляет наследник, а форма пробы общая.
 *
 * <p><b>Ключ {@code live} есть у обеих, и он общий.</b> Его производит
 * предикат формы, а не поле; примесью он не изымается, а на обратном ходе
 * отбрасывается как неизвестное свойство — и проходит это только на маппере
 * сборки бина (§«Состав ключей строки задаёт не перечень полей»).
 */
public abstract class InstrumentRulesOverlayCopyContract extends JsonbOverlayProbe {

    /** Ключ, которого в строке нет ни у одного поля формы: его даёт предикат. */
    private static final String PREDICATE_KEY = "live";

    /** Строка `U7.3`: объявлена один раз и сверяется каждой копией. */
    private static final String SHARED_JSON =
            "{\"instrumentType\":\"SWAP\",\"contractType\":\"LINEAR\",\"status\":\"LIVE\","
                    + "\"externalInstrumentId\":\"BTC-USDT-SWAP\",\"externalTickSize\":\"0.1\","
                    + "\"live\":true}";

    // --- порты к своей копии конвертера ---------------------------------

    protected abstract String writeRules(InstrumentExternalRules rules);

    protected abstract InstrumentExternalRules readRules(String json);

    /** Поле, которое примесь ЭТОЙ копии из навеса изымает. */
    protected abstract String excludedField();

    /** Поле, которое изымает копия-близнец, а эта — удерживает. */
    protected abstract String retainedField();

    // --- U7: изъятия примесью и расхождение двух копий -------------------

    @Test
    @DisplayName("U7.1/U7.2 — примесь изымает своё поле, чужое остаётся, ключ предиката есть")
    protected void u7_1_theMixinRemovesItsOwnFieldAndKeepsTheSiblingOne() {
        InstrumentExternalRules rules = fullRules();

        String json = writeRules(rules);

        assertThat(keysOf(json)).doesNotContain(excludedField());
        assertThat(keysOf(json)).contains(retainedField(), PREDICATE_KEY);
        InstrumentExternalRules read = readRules(json);
        assertThat(read).usingRecursiveComparison()
                .ignoringFields(excludedField()).isEqualTo(rules);
        assertThat(fieldValueOf(read, excludedField()))
                .as("изъятое поле у перечитанного пусто — навес его не нёс")
                .isNull();
    }

    @Test
    @DisplayName("U7.3 — на объекте без изымаемых полей строки копий совпадают дословно")
    protected void u7_3_withoutTheRemovedFieldsBothCopiesWriteTheSameString() {
        InstrumentExternalRules rules = sharedRules();

        String json = writeRules(rules);

        assertThat(json).isEqualTo(SHARED_JSON);
        assertThat(keysOf(json)).contains(PREDICATE_KEY);
    }

    @Test
    @DisplayName("U7.5 — нечисловую строку навес переносит дословно, гасит её модель")
    protected void u7_5_aNonNumericSpecIsCarriedVerbatimAndDampedByTheModel() {
        InstrumentExternalRules rules = sharedRules();
        rules.setExternalTickSize("n/a");

        InstrumentExternalRules read = readRules(writeRules(rules));

        assertThat(read.getExternalTickSize()).isEqualTo("n/a");
        assertThat(read.tickSize()).isNull();
    }

    @Test
    @DisplayName("U7.6 — пустота зеркальна у обеих сторон")
    protected void u7_6_emptinessIsMirroredOnBothSides() {
        assertThat(writeRules(null)).isNull();
        assertThat(readRules(null)).isNull();
    }

    @Test
    @DisplayName("U7.7 — неизвестное поле строки отбрасывается, прочее тождественно")
    protected void u7_7_anUnknownFieldIsDroppedAndTheRestIsIdentical() {
        InstrumentExternalRules rules = sharedRules();
        String withUnknown = writeRules(rules)
                .replaceFirst("\\{", "{\"externalSettlementCurrency\":\"USDT\",");

        assertThat(readRules(withUnknown)).usingRecursiveComparison().isEqualTo(rules);
    }

    @Test
    @DisplayName("U7.8 — имя статуса торгуемости вне перечня отказывает своим классом")
    protected void u7_8_anUnknownTradabilityStatusFailsWithTheOwnClass() {
        String json = "{\"status\":\"HALTED\"}";

        assertThatThrownBy(() -> readRules(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deserialization")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    // --- U10.3: тождество копий, которого у этой пары нет по существу ----

    @Test
    @DisplayName("U10.3 — правила копий разные, ключ предиката общий")
    protected void u10_3_theCopiesCarryDifferentRulesAndACommonPredicateKey() {
        String json = writeRules(fullRules());

        assertThat(excludedField())
                .as("изъятое и удержанное — разные поля: иначе пара не расходилась бы вовсе")
                .isNotEqualTo(retainedField());
        assertThat(keysOf(json)).contains(PREDICATE_KEY);
        assertThat(keysOf(json)).doesNotContain(excludedField());
        assertThat(keysOf(json)).contains(retainedField());
    }

    // --- материал кейсов --------------------------------------------------

    /** Объект со ВСЕМИ полями, которые изымает хоть одна из копий. */
    private static InstrumentExternalRules fullRules() {
        InstrumentExternalRules rules = sharedRules();
        rules.setInstrumentId(77L);
        rules.setExternalTakerFeeRate("0.0005");
        return rules;
    }

    /** Объект без единого изымаемого поля: на нём строки копий совпадают. */
    private static InstrumentExternalRules sharedRules() {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setInstrumentType(InstrumentExternalRules.InstrumentType.SWAP);
        rules.setContractType(InstrumentExternalRules.ContractType.LINEAR);
        rules.setStatus(InstrumentExternalRules.Status.LIVE);
        rules.setExternalInstrumentId("BTC-USDT-SWAP");
        rules.setExternalTickSize("0.1");
        return rules;
    }

    private static Object fieldValueOf(InstrumentExternalRules rules, String field) {
        return switch (field) {
            case "instrumentId" -> rules.getInstrumentId();
            case "externalTakerFeeRate" -> rules.getExternalTakerFeeRate();
            default -> throw new IllegalArgumentException("Изымаемое поле не объявлено: " + field);
        };
    }
}
