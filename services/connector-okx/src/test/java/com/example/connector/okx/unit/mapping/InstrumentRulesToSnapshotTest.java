package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.mapping.InstrumentExternalRulesMapper;
import com.example.connector.okx.snapshot.InstrumentExternalRulesExternalSnapshot;
import com.example.connector.okx.snapshot.PositionTierExternalSnapshot;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.PositionTier;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Правила инструмента → снапшот и резолв трёх перечней — группа `U15`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/InstrumentExternalRules.md
 * §«`InstrumentOkxResponse` → snapshot» и §«Резолв enum'ов при
 * материализации (`snapshotToDomain`)»).
 *
 * <p><b>Базовая сборка:</b> тот же {@code InstrumentOkxResponse}, что у
 * `U14`; второй переход — материализация из снапшота, с ключом навеса и
 * без него.
 *
 * <p><b>Два этажа разведены, и разведение объявлено домом:</b> снапшот
 * держит только сырые строки, доменные проекции резолвятся при
 * материализации. Кейсы снапшота поэтому не спрашивают перечней, а кейсы
 * материализации подают сырые строки прямо.
 */
class InstrumentRulesToSnapshotTest {

    private final InstrumentExternalRulesMapper mapper = Mappers.instrumentRules();

    private static InstrumentExternalRulesExternalSnapshot.InstrumentExternalRulesExternalSnapshotBuilder
            snapshot() {
        return InstrumentExternalRulesExternalSnapshot.builder()
                .externalInstrumentId(OkxFixture.INSTRUMENT)
                .externalInstrumentType("SWAP")
                .externalContractType("linear")
                .externalContractValue("0.1")
                .externalContractValueCurrency("ETH")
                .externalTickSize("0.01")
                .externalLotSize("1")
                .externalMinSize("1")
                .externalMaxLimitSize("1000000")
                .externalMaxMarketSize("100000")
                .externalMaxTriggerSize("100000")
                .externalMaxStopSize("100000")
                .externalMaxLeverage("10")
                .externalState("live")
                .externalFeeGroupId("1");
    }

    @Test
    @DisplayName("U15.1 — пятнадцать сырых строк переносятся один к одному, перечни не резолвятся")
    void u15_1_fifteenRawStringsAreCarriedOneToOne() {
        InstrumentExternalRulesExternalSnapshot built =
                mapper.integrationToSnapshot(OkxFixture.instrument(), null);

        assertThat(built.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(built.getExternalInstrumentType()).isEqualTo("SWAP");
        assertThat(built.getExternalContractType()).isEqualTo("linear");
        assertThat(built.getExternalContractValue()).isEqualTo("0.1");
        assertThat(built.getExternalContractValueCurrency()).isEqualTo("ETH");
        assertThat(built.getExternalTickSize()).isEqualTo("0.01");
        assertThat(built.getExternalLotSize()).isEqualTo("1");
        assertThat(built.getExternalMinSize()).isEqualTo("1");
        assertThat(built.getExternalMaxLimitSize()).isEqualTo("1000000");
        assertThat(built.getExternalMaxMarketSize()).isEqualTo("100000");
        assertThat(built.getExternalMaxTriggerSize()).isEqualTo("100000");
        assertThat(built.getExternalMaxStopSize()).isEqualTo("100000");
        assertThat(built.getExternalMaxLeverage()).isEqualTo("10");
        assertThat(built.getExternalState()).isEqualTo("live");
        assertThat(built.getExternalFeeGroupId()).isEqualTo("1");
        assertThat(InstrumentExternalRulesExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .allSatisfy(name -> assertThat(name).startsWith("external"));
    }

    /** Их дом — инструмент. */
    @Test
    @DisplayName("U15.2 — трёх валют в снапшоте правил нет")
    void u15_2_theThreeCurrenciesAreAbsentHere() {
        assertThat(InstrumentExternalRulesExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("externalBaseCurrency", "externalQuoteCurrency",
                        "externalSettleCurrency");
    }

    @Test
    @DisplayName("U15.3 — полей площадки вне худой формы источника в снапшоте нет")
    void u15_3_fieldsOutsideTheThinSourceFormAreAbsent() {
        assertThat(InstrumentExternalRulesExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("externalContractMultiplier", "externalInstrumentFamily",
                        "externalUnderlying", "externalListTime", "externalExpiryTime");
    }

    @Test
    @DisplayName("U15.4 — материализация с ключом навеса: проекция и ключ")
    void u15_4_materializationWithTheOverlayKey() {
        InstrumentExternalRules rules = mapper.snapshotToDomain(snapshot().build(), 42L);

        assertThat(rules.getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.SWAP);
        assertThat(rules.getInstrumentId()).isEqualTo(42L);
        assertThat(rules.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(rules.getExternalTickSize()).isEqualTo("0.01");
        assertThat(rules.getExternalFeeGroupId()).isEqualTo("1");
    }

    @Test
    @DisplayName("U15.5 — регистр типа инструмента подбирается")
    void u15_5_theInstrumentTypeIsCaseInsensitive() {
        assertThat(mapper.snapshotToDomain(snapshot().externalInstrumentType("swap").build(), 42L)
                .getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.SWAP);
    }

    @Test
    @DisplayName("U15.6 — обрамляющие пробелы типа инструмента снимаются")
    void u15_6_theInstrumentTypeIsTrimmed() {
        assertThat(mapper.snapshotToDomain(snapshot().externalInstrumentType(" FUTURES ").build(), 42L)
                .getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.FUTURES);
    }

    /** Тип инструмента ничего не гейтит, поэтому неизвестное нормализуется. */
    @Test
    @DisplayName("U15.7 — неизвестный тип инструмента нормализуется, а не отказывает")
    void u15_7_anUnknownInstrumentTypeIsNormalized() {
        assertThat(mapper.snapshotToDomain(snapshot().externalInstrumentType("PERP").build(), 42L)
                .getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.UNKNOWN);
    }

    @Test
    @DisplayName("U15.8 — пустой и отсутствующий тип инструмента дают тот же исход")
    void u15_8_anEmptyInstrumentTypeIsUnknownToo() {
        assertThat(mapper.snapshotToDomain(snapshot().externalInstrumentType("").build(), 42L)
                .getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.UNKNOWN);
        assertThat(mapper.snapshotToDomain(snapshot().externalInstrumentType(null).build(), 42L)
                .getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({"LINEAR,LINEAR", "INVERSE,INVERSE"})
    @DisplayName("U15.9 — оба типа контракта резолвятся")
    void u15_9_bothContractTypesResolve(String raw, InstrumentExternalRules.ContractType expected) {
        assertThat(mapper.snapshotToDomain(snapshot().externalContractType(raw).build(), 42L)
                .getContractType()).isEqualTo(expected);
    }

    @Test
    @DisplayName("U15.10 — неизвестный тип контракта нормализуется")
    void u15_10_anUnknownContractTypeIsNormalized() {
        assertThat(mapper.snapshotToDomain(snapshot().externalContractType("quanto").build(), 42L)
                .getContractType()).isEqualTo(InstrumentExternalRules.ContractType.UNKNOWN);
    }

    /** Статус торгуемости читает преконтроль риска — перечень обходится целиком. */
    @ParameterizedTest
    @CsvSource({"live,LIVE", "suspend,SUSPEND", "preopen,PREOPEN", "expired,EXPIRED", "test,TEST"})
    @DisplayName("U15.11 — все пять статусов торгуемости резолвятся")
    void u15_11_allFiveTradabilityStatusesResolve(String raw, InstrumentExternalRules.Status expected) {
        assertThat(mapper.snapshotToDomain(snapshot().externalState(raw).build(), 42L).getStatus())
                .isEqualTo(expected);
    }

    /** Неизвестный статус не равен «торгуется»: преконтроль читает его запрещающе. */
    @Test
    @DisplayName("U15.12 — статус вне перечня нормализуется в неизвестный")
    void u15_12_anUnknownStatusIsNotTradable() {
        InstrumentExternalRules rules =
                mapper.snapshotToDomain(snapshot().externalState("delisted").build(), 42L);

        assertThat(rules.getStatus()).isEqualTo(InstrumentExternalRules.Status.UNKNOWN);
        assertThat(rules.isLive()).isFalse();
    }

    /** Числовой ключ базы границу сервиса не переходит: его ставит владелец. */
    @Test
    @DisplayName("U15.13 — материализация без ключа навеса: ключ пуст, прочее на месте")
    void u15_13_materializationWithoutTheOverlayKey() {
        InstrumentExternalRules rules = mapper.snapshotToDomain(snapshot().build());

        assertThat(rules.getInstrumentId()).isNull();
        assertThat(rules.getInstrumentType()).isEqualTo(InstrumentExternalRules.InstrumentType.SWAP);
        assertThat(rules.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
    }

    /** Тиры едут в снапшот сырыми строками, как прочие поля правил. */
    @Test
    @DisplayName("U15.15 — позиционные тиры семьи едут в снапшот сырыми строками в порядке ответа")
    void u15_15_positionTiersAreCarriedRawInResponseOrder() {
        InstrumentExternalRulesExternalSnapshot built = mapper.integrationToSnapshot(OkxFixture.instrument(),
                List.of(OkxFixture.positionTier("1", "0", "1000", "0.004"),
                        OkxFixture.positionTier("2", "1000", "5000", "0.006")));

        assertThat(built.getExternalPositionTiers()).hasSize(2);
        PositionTierExternalSnapshot second = built.getExternalPositionTiers().get(1);
        assertThat(second.getExternalMinSize()).isEqualTo("1000");
        assertThat(second.getExternalMaxSize()).isEqualTo("5000");
        assertThat(second.getExternalMaintenanceMarginRate()).isEqualTo("0.006");
        assertThat(built.getExternalTickSize()).isEqualTo("0.01");
    }

    /** Разбор в числа — на материализации, как у прочих чисел правил. */
    @Test
    @DisplayName("U15.16 — материализация разбирает тиры в числа")
    void u15_16_materializationParsesTheTiers() {
        InstrumentExternalRules rules = mapper.snapshotToDomain(snapshot()
                .externalPositionTiers(List.of(PositionTierExternalSnapshot.builder()
                        .externalMinSize("0")
                        .externalMaxSize("1000")
                        .externalMaintenanceMarginRate("0.004")
                        .build()))
                .build());

        assertThat(rules.getPositionTiers()).hasSize(1);
        PositionTier tier = rules.getPositionTiers().getFirst();
        assertThat(tier.minSize()).isEqualByComparingTo("0");
        assertThat(tier.maxSize()).isEqualByComparingTo("1000");
        assertThat(tier.maintenanceMarginRate()).isEqualByComparingTo("0.004");
    }

    /** Пустота остаётся пустотой: «не прочли» и «нет» оценке ликвидации неразличимы. */
    @Test
    @DisplayName("U15.17 — правила без тиров материализуются с пустыми тирами, а не с пустым перечнем")
    void u15_17_rulesWithoutTiersKeepTheTiersEmpty() {
        InstrumentExternalRules rules = mapper.snapshotToDomain(
                mapper.integrationToSnapshot(OkxFixture.instrument(), null), 42L);

        assertThat(rules.getPositionTiers()).isNull();
        assertThat(rules.getExternalTickSize()).isEqualTo("0.01");
    }

    /** Неразбираемое число отвергает сеть разбора шлюза — классом, который она и ловит. */
    @Test
    @DisplayName("U15.18 — неразбираемое число тира отказывает на материализации")
    void u15_18_anUnparsableTierNumberFailsOnMaterialization() {
        InstrumentExternalRulesExternalSnapshot broken = snapshot()
                .externalPositionTiers(List.of(PositionTierExternalSnapshot.builder()
                        .externalMinSize("0")
                        .externalMaxSize("1000")
                        .externalMaintenanceMarginRate("n/a")
                        .build()))
                .build();

        assertThatThrownBy(() -> mapper.snapshotToDomain(broken, 42L))
                .isInstanceOf(NumberFormatException.class);
    }

    /** Охрана конъюнктивна (звено `Z1`): пустота обоих источников — и только она — даёт пустоту. */
    @Test
    @DisplayName("U15.14 — пустота вместо снапшота: порознь у формы без ключа и с ключом")
    void u15_14_emptinessMeetsAConjunctiveGuard() {
        assertThat(mapper.snapshotToDomain((InstrumentExternalRulesExternalSnapshot) null)).isNull();

        InstrumentExternalRules withKeyOnly = mapper.snapshotToDomain(null, 42L);

        assertThat(withKeyOnly).isNotNull();
        assertThat(withKeyOnly.getInstrumentId()).isEqualTo(42L);
        assertThat(withKeyOnly.getInstrumentType()).isNull();
        assertThat(withKeyOnly.getContractType()).isNull();
        assertThat(withKeyOnly.getStatus()).isNull();
        assertThat(withKeyOnly.getExternalInstrumentId()).isNull();
    }
}
