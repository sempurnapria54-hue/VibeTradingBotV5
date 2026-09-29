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
 * <p><b>Правило изъятия у копий одно, и ожидание объявлено один раз.</b>
 * {@code InstrumentExternalRulesJsonConverter} живёт двумя экземплярами, и
 * примесь у обоих изымает ОБА поля — идентификатор владельца и ставку
 * комиссии (docs/models/domain/other/InstrumentExternalRules.md §«Ставка
 * комиссии»). Наследник поэтому подаёт только порты к своей копии, а
 * тождество копий мерит дословная строка на объекте со всеми полями.
 *
 * <p><b>Ключ {@code live} есть у обеих, и он общий.</b> Его производит
 * предикат формы, а не поле; примесью он не изымается, а на обратном ходе
 * отбрасывается как неизвестное свойство — и проходит это только на маппере
 * сборки бина (§«Состав ключей строки задаёт не перечень полей»).
 */
public abstract class InstrumentRulesOverlayCopyContract extends JsonbOverlayProbe {

    /** Ключ, которого в строке нет ни у одного поля формы: его даёт предикат. */
    private static final String PREDICATE_KEY = "live";

    /** Изымаемые поля: у обеих копий одни и те же. */
    private static final String OWNER_ID_FIELD = "instrumentId";

    private static final String FEE_RATE_FIELD = "externalTakerFeeRate";

    /** Строка `U7.3` и `U10.3`: объявлена один раз и сверяется каждой копией. */
    private static final String SHARED_JSON =
            "{\"instrumentType\":\"SWAP\",\"contractType\":\"LINEAR\",\"status\":\"LIVE\","
                    + "\"externalInstrumentId\":\"BTC-USDT-SWAP\",\"externalTickSize\":\"0.1\","
                    + "\"live\":true}";

    // --- порты к своей копии конвертера ---------------------------------

    protected abstract String writeRules(InstrumentExternalRules rules);

    protected abstract InstrumentExternalRules readRules(String json);

    // --- U7: изъятия примесью ---------------------------------------------

    @Test
    @DisplayName("U7.1/U7.2 — примесь изымает оба поля, ключ предиката есть")
    protected void u7_1_theMixinRemovesBothFieldsAndKeepsThePredicateKey() {
        InstrumentExternalRules rules = fullRules();

        String json = writeRules(rules);

        assertThat(keysOf(json)).doesNotContain(OWNER_ID_FIELD, FEE_RATE_FIELD);
        assertThat(keysOf(json)).contains(PREDICATE_KEY);
        InstrumentExternalRules read = readRules(json);
        assertThat(read).usingRecursiveComparison()
                .ignoringFields(OWNER_ID_FIELD, FEE_RATE_FIELD).isEqualTo(rules);
        assertThat(read.getInstrumentId())
                .as("идентификатор владельца у перечитанного пуст — навес его не нёс")
                .isNull();
        assertThat(read.getExternalTakerFeeRate())
                .as("ставка у перечитанного пуста — навес её не нёс")
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

    // --- U10.3: тождество копий на объекте со всеми полями ---------------

    @Test
    @DisplayName("U10.3 — на объекте со всеми полями строки копий совпадают дословно")
    protected void u10_3_withEveryFieldSetBothCopiesWriteTheSameString() {
        String json = writeRules(fullRules());

        assertThat(json)
                .as("изъятые поля строку не меняют: у копий одно правило изъятия")
                .isEqualTo(SHARED_JSON);
    }

    // --- материал кейсов --------------------------------------------------

    /** Объект со ВСЕМИ изымаемыми полями. */
    private static InstrumentExternalRules fullRules() {
        InstrumentExternalRules rules = sharedRules();
        rules.setInstrumentId(77L);
        rules.setExternalTakerFeeRate("0.0005");
        return rules;
    }

    /** Объект без единого изымаемого поля. */
    private static InstrumentExternalRules sharedRules() {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setInstrumentType(InstrumentExternalRules.InstrumentType.SWAP);
        rules.setContractType(InstrumentExternalRules.ContractType.LINEAR);
        rules.setStatus(InstrumentExternalRules.Status.LIVE);
        rules.setExternalInstrumentId("BTC-USDT-SWAP");
        rules.setExternalTickSize("0.1");
        return rules;
    }
}
