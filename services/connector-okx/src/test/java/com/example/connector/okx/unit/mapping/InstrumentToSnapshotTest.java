package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.InstrumentOkxResponse;
import com.example.connector.okx.mapping.InstrumentMapper;
import com.example.connector.okx.snapshot.InstrumentExternalSnapshot;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Инструмент → граничный снапшот — группа `U14` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Instrument.md §«`InstrumentOkxResponse` →
 * snapshot → domain (идентичность + биржевые поля)» и
 * §«`InstrumentOkxResponse` → snapshot → domain (валюты)»).
 *
 * <p><b>Базовая сборка:</b> {@code InstrumentOkxResponse} со всеми
 * девятнадцатью полями худой формы источника непустыми.
 */
class InstrumentToSnapshotTest {

    private final InstrumentMapper mapper = Mappers.instrument();

    @Test
    @DisplayName("U14.1 — идентичность и биржевые поля переносятся сырыми строками")
    void u14_1_identityAndExchangeFieldsStayRawStrings() {
        InstrumentExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.instrument());

        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getExternalInstrumentType()).isEqualTo("SWAP");
        assertThat(snapshot.getExternalStatus()).isEqualTo("live");
        assertThat(snapshot.getExternalLeverage()).isEqualTo("10");
    }

    @Test
    @DisplayName("U14.2 — три валюты переносятся")
    void u14_2_threeCurrenciesAreCarried() {
        InstrumentExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.instrument());

        assertThat(snapshot.getExternalBaseCurrency()).isEqualTo("ETH");
        assertThat(snapshot.getExternalQuoteCurrency()).isEqualTo("USDT");
        assertThat(snapshot.getExternalSettleCurrency()).isEqualTo("USDT");
    }

    /** Числами их делает навес правил, а не этот переход. */
    @Test
    @DisplayName("U14.3 — пять справочных полей переносятся строками")
    void u14_3_fiveReferenceFieldsStayStrings() {
        InstrumentExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.instrument());

        assertThat(snapshot.getExternalLotSize()).isEqualTo("1");
        assertThat(snapshot.getExternalMinSize()).isEqualTo("1");
        assertThat(snapshot.getExternalContractValue()).isEqualTo("0.1");
        assertThat(snapshot.getExternalContractMultiplier()).isEqualTo("1");
        assertThat(snapshot.getExternalTickSize()).isEqualTo("0.01");
    }

    /** У них своя форма — снапшот правил. */
    @Test
    @DisplayName("U14.4 — полей навеса правил в этом снапшоте нет")
    void u14_4_theRulesOverlayFieldsAreAbsentHere() {
        assertThat(InstrumentExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("externalContractValueCurrency", "externalContractType",
                        "externalFeeGroupId", "externalMaxLimitSize", "externalMaxMarketSize",
                        "externalMaxTriggerSize", "externalMaxStopSize");
    }

    /** Онбординговый статус системы проекцией биржевого не является. */
    @Test
    @DisplayName("U14.5 — биржевой статус живёт сырым")
    void u14_5_theExchangeStatusStaysRaw() {
        InstrumentOkxResponse response = OkxFixture.instrument();
        response.setState("suspend");

        assertThat(mapper.integrationToSnapshot(response).getExternalStatus()).isEqualTo("suspend");
    }

    @Test
    @DisplayName("U14.6 — пустое плечо: пустая строка, отказа нет")
    void u14_6_anEmptyLeverageIsAnEmptyString() {
        InstrumentOkxResponse response = OkxFixture.instrument();
        response.setLever("");

        assertThat(mapper.integrationToSnapshot(response).getExternalLeverage()).isNotNull().isEmpty();
    }

    /** У спота расчётной валюты нет, и отказ здесь запретил бы законный вход. */
    @Test
    @DisplayName("U14.7 — спотовая пара без расчётной валюты: пустая строка")
    void u14_7_aSpotPairCarriesAnEmptySettlementCurrency() {
        InstrumentOkxResponse response = OkxFixture.instrument();
        response.setSettleCcy("");

        assertThat(mapper.integrationToSnapshot(response).getExternalSettleCurrency())
                .isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U14.8 — пустота вместо формы источника даёт пустоту")
    void u14_8_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot(null)).isNull();
    }
}
