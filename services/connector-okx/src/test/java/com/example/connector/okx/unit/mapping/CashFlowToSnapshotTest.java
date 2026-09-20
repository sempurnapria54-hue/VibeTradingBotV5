package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.AccountBillOkxResponse;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.snapshot.DealCashFlowExternalSnapshot;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Движение средств → граничный снапшот — группа `U18` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/DealCashFlow.md §«Граничный снапшот» и
 * §«Разделение ролей суммы и комиссии»).
 *
 * <p><b>Базовая сборка:</b> {@code AccountBillOkxResponse} со всеми
 * непустыми полями.
 */
class CashFlowToSnapshotTest {

    private final DealCashFlowMapper mapper = Mappers.cashFlow();

    @Test
    @DisplayName("U18.1 — базовая сборка: идентичность, три числа, сырые типы и момент")
    void u18_1_theBaseAssemblyLandsFieldByField() {
        DealCashFlowExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.cashFlow());

        assertThat(snapshot.getExternalBillId()).isEqualTo("b1");
        assertThat(snapshot.getAmount()).isEqualByComparingTo("12.5");
        assertThat(snapshot.getPositionBalanceChange()).isEqualByComparingTo("-3");
        assertThat(snapshot.getExternalFee()).isEqualByComparingTo("-0.25");
        assertThat(snapshot.getCcy()).isEqualTo("USDT");
        assertThat(snapshot.getExternalType()).isEqualTo("2");
        assertThat(snapshot.getExternalSubType()).isEqualTo("173");
        assertThat(snapshot.getExternalOrderId()).isEqualTo("1");
        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
    }

    /** Это не факты ответа, их производит вызывающий. */
    @Test
    @DisplayName("U18.2 — категории, биржи, курса, координат свечи и ссылки на сделку в снапшоте нет")
    void u18_2_theProducedFactsAreAbsentFromTheSnapshot() {
        assertThat(DealCashFlowExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("category", "exchangeAccountId", "appliedRate", "rateStatus",
                        "appliedRateCandleInstrument", "appliedRateCandleTimeframe",
                        "appliedRateCandleOpenTime", "dealId");
    }

    /** Это операнд сверки, и правый операнд у него тоже сырой. */
    @Test
    @DisplayName("U18.3 — знак комиссии сырой")
    void u18_3_theFeeSignStaysRaw() {
        AccountBillOkxResponse response = OkxFixture.cashFlow();
        response.setFee("-0.25");

        assertThat(mapper.integrationToSnapshot(response).getExternalFee())
                .isEqualByComparingTo("-0.25");
    }

    /** Несобытийная конвенция к движению не применяется: «вычитать нечего» решает арифметика сверки. */
    @Test
    @DisplayName("U18.4 — пустая комиссия есть пустота, не ноль")
    void u18_4_anEmptyFeeIsEmptinessNotZero() {
        AccountBillOkxResponse response = OkxFixture.cashFlow();
        response.setFee("");

        assertThat(mapper.integrationToSnapshot(response).getExternalFee()).isNull();
    }

    /** На изолированной марже расчёт финансирования ложится в изменение маржи. */
    @Test
    @DisplayName("U18.5 — нулевое изменение баланса при ненулевом изменении маржи")
    void u18_5_aZeroBalanceChangeWithANonZeroMarginChange() {
        AccountBillOkxResponse response = OkxFixture.cashFlow();
        response.setBalChg("0");
        response.setPosBalChg("-3");

        DealCashFlowExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getAmount()).isEqualByComparingTo("0");
        assertThat(snapshot.getPositionBalanceChange()).isEqualByComparingTo("-3");
    }

    /** Движение, с заявкой не связанное, законно — но отличает его `isBlank`, а не пустота. */
    @Test
    @DisplayName("U18.6 — движение без заявки: пустая строка")
    void u18_6_anUnlinkedMovementCarriesAnEmptyString() {
        AccountBillOkxResponse response = OkxFixture.cashFlow();
        response.setOrdId("");

        assertThat(mapper.integrationToSnapshot(response).getExternalOrderId()).isNotNull().isEmpty();
    }

    /** Резолв категории — не здесь: маппер доменных решений не принимает. */
    @Test
    @DisplayName("U18.7 — пустой подтип: пустая строка, категория не резолвится")
    void u18_7_anEmptySubTypeIsAnEmptyString() {
        AccountBillOkxResponse response = OkxFixture.cashFlow();
        response.setSubType("");

        assertThat(mapper.integrationToSnapshot(response).getExternalSubType()).isNotNull().isEmpty();
    }

    /** Без момента движение в окно линковки не попадает. */
    @Test
    @DisplayName("U18.8 — пустой момент движения даёт пустоту")
    void u18_8_anEmptyMomentIsEmptiness() {
        AccountBillOkxResponse response = OkxFixture.cashFlow();
        response.setTs("");

        assertThat(mapper.integrationToSnapshot(response).getExternalCreatedAt()).isNull();
    }

    @Test
    @DisplayName("U18.9 — пустота вместо формы источника даёт пустоту")
    void u18_9_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot(null)).isNull();
    }
}
