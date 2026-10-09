package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.PositionTier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Кейсы навеса справочных правил инструмента: группа `U7`, клетки `U10.3`,
 * `U11.7`, `U13.3` и `U13.6` документа
 * `.claude/tests/cases/jsonb-overlay-roundtrip.md`.
 *
 * <p><b>Правило изъятия у копий одно, и ожидание объявлено один раз.</b>
 * {@code InstrumentExternalRulesJsonConverter} живёт двумя экземплярами, и
 * примесь у обоих изымает ОБА поля — идентификатор владельца и ставку
 * комиссии (docs/models/domain/other/InstrumentExternalRules.md §«Ставка
 * комиссии»). Наследник поэтому подаёт только порты к своей копии, а
 * тождество копий мерит дословная строка на объекте со всеми полями.
 *
 * <p><b>Ключа {@code live} нет ни у одной копии.</b> Его производил бы
 * предикат формы, а не поле; примесь хранилищного слоя его не задевает, и
 * изъят он на самой модели (§«Состав ключей строки задаёт не перечень
 * полей»). Состав строки пинится литеральным перечнем (`U13.3`): перечень,
 * выведенный из класса формы, уехал бы вместе с переименованием. Вложенная
 * форма — строка позиционного тира — пинится своим перечнем (`U13.6`):
 * пин словом действует на каждом уровне вложенности
 * (docs/rules/persistence-representation.md §«Состав ключей строки навеса»).
 */
public abstract class InstrumentRulesOverlayCopyContract extends JsonbOverlayProbe {

    /** Ключ, который дал бы предикат формы, не будь он изъят на модели. */
    private static final String PREDICATE_KEY = "live";

    /** Изымаемые поля: у обеих копий одни и те же. */
    private static final String OWNER_ID_FIELD = "instrumentId";

    private static final String FEE_RATE_FIELD = "externalTakerFeeRate";

    /** Строка `U7.3` и `U10.3`: объявлена один раз и сверяется каждой копией. */
    private static final String SHARED_JSON =
            "{\"instrumentType\":\"SWAP\",\"contractType\":\"LINEAR\",\"status\":\"LIVE\","
                    + "\"externalInstrumentId\":\"BTC-USDT-SWAP\",\"externalTickSize\":\"0.1\"}";

    // --- порты к своей копии конвертера ---------------------------------

    protected abstract String writeRules(InstrumentExternalRules rules);

    protected abstract InstrumentExternalRules readRules(String json);

    /**
     * Чтение той же копией, собранной на ЧУЖОМ маппере: клетка `U11.7`
     * мерит, что терпимость к неизвестному свойству конструктор ставит
     * безусловно.
     */
    protected abstract InstrumentExternalRules readRulesOn(ObjectMapper source, String json);

    // --- U7: изъятия примесью ---------------------------------------------

    @Test
    @DisplayName("U7.1/U7.2 — примесь изымает оба поля, ключа предиката нет")
    protected void u7_1_theMixinRemovesBothFieldsAndThePredicateKeyIsAbsent() {
        InstrumentExternalRules rules = fullRules();

        String json = writeRules(rules);

        assertThat(keysOf(json)).doesNotContain(OWNER_ID_FIELD, FEE_RATE_FIELD);
        assertThat(keysOf(json))
                .as("предикат формы изъят на модели, а не примесью: он не данные ни у одного "
                        + "сериализатора")
                .doesNotContain(PREDICATE_KEY);
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
        assertThat(keysOf(json)).doesNotContain(PREDICATE_KEY);
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
        assertThat(keysOf(json)).doesNotContain(PREDICATE_KEY);
    }

    // --- U11.7: терпимость к неизвестному свойству пинит конвертер -------

    /**
     * Копия, собранная на маппере со СТРОГОЙ охраной неизвестного свойства,
     * читает свою строку с лишним ключом: терпимость пинит конструктор, и
     * настройка источника её не сдвигает.
     *
     * <p>Лишний ключ — не {@code live} прежних строк: имя, изъятое
     * {@code @JsonIgnore}, читатель пропускает как объявленно игнорируемое и
     * на строгом маппере (прочитано по исходникам библиотеки), то есть пина
     * конвертера оно не мерило бы.
     */
    @Test
    @DisplayName("U11.7 — на строгом источнике лишний ключ своей строки отброшен, прочее тождественно")
    protected void u11_7_onAStrictSourceAnUnknownKeyOfTheOwnRowIsDropped() {
        ObjectMapper strict = beanAssemblyMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        InstrumentExternalRules rules = sharedRules();
        String withUnknown = writeRules(rules)
                .replaceFirst("\\{", "{\"externalSettlementCurrency\":\"USDT\",");

        assertThat(readRulesOn(strict, withUnknown))
                .as("U11.7: вход %s, источник строгий — терпимость запинена конвертером", withUnknown)
                .usingRecursiveComparison().isEqualTo(rules);
    }

    // --- U13.3: состав ключей строки — литеральным перечнем -------------

    @Test
    @DisplayName("U13.3 — объект со всеми полями: ключи строки равны литеральному перечню")
    protected void u13_3_theRowKeysOfAFullObjectEqualTheLiteralList() {
        String json = writeRules(everyFieldRules());

        assertThat(keysOf(json))
                .as("U13.3: поля данных формы за вычетом идентификатора владельца и ставки; "
                        + "ключа предиката нет")
                .containsExactlyInAnyOrder(
                        "instrumentType",
                        "contractType",
                        "status",
                        "externalInstrumentType",
                        "externalInstrumentId",
                        "externalContractType",
                        "externalContractValue",
                        "externalContractValueCurrency",
                        "externalTickSize",
                        "externalLotSize",
                        "externalMinSize",
                        "externalMaxLimitSize",
                        "externalMaxMarketSize",
                        "externalMaxTriggerSize",
                        "externalMaxStopSize",
                        "externalMaxLeverage",
                        "externalState",
                        "externalFeeGroupId",
                        "positionTiers");
    }

    // --- U13.6: состав ключей вложенной формы — строки тира -------------

    @Test
    @DisplayName("U13.6 — тир со всеми полями: ключи строки тира равны литеральному перечню")
    protected void u13_6_theKeysOfAFullPositionTierEqualTheLiteralList() {
        String json = writeRules(everyFieldRules());

        JsonNode tiers = readTree(json).get("positionTiers");

        assertThat(tiers)
                .as("U13.6: тиры заполнены — ключ перечня в строке есть")
                .isNotNull();
        assertThat(tiers.isArray())
                .as("U13.6: тиры едут перечнем строк, а не значением")
                .isTrue();
        assertThat(keysOf(tiers.get(0).toString()))
                .as("U13.6: строка позиционного тира — три поля данных записи; предикат "
                        + "покрытия с параметром ключом не становится")
                .containsExactlyInAnyOrder(
                        "minSize",
                        "maxSize",
                        "maintenanceMarginRate");
    }

    // --- материал кейсов --------------------------------------------------

    /** Объект со ВСЕМИ изымаемыми полями. */
    private static InstrumentExternalRules fullRules() {
        InstrumentExternalRules rules = sharedRules();
        rules.setInstrumentId(77L);
        rules.setExternalTakerFeeRate("0.0005");
        return rules;
    }

    /** Объект, у которого заполнено КАЖДОЕ поле формы, включая изымаемые. */
    private static InstrumentExternalRules everyFieldRules() {
        InstrumentExternalRules rules = fullRules();
        rules.setExternalInstrumentType("SWAP");
        rules.setExternalContractType("linear");
        rules.setExternalContractValue("0.01");
        rules.setExternalContractValueCurrency("BTC");
        rules.setExternalLotSize("1");
        rules.setExternalMinSize("1");
        rules.setExternalMaxLimitSize("100000");
        rules.setExternalMaxMarketSize("5000");
        rules.setExternalMaxTriggerSize("100000");
        rules.setExternalMaxStopSize("5000");
        rules.setExternalMaxLeverage("100");
        rules.setExternalState("live");
        rules.setExternalFeeGroupId("1");
        rules.setPositionTiers(List.of(new PositionTier(
                new BigDecimal("1"), new BigDecimal("1000"), new BigDecimal("0.004"))));
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
