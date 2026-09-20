package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.BalanceDetailOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.BalanceOkxResponse;
import com.example.connector.okx.mapping.BalanceContainerMapper;
import com.example.connector.okx.snapshot.BalanceContainerExternalSnapshot;
import com.example.connector.okx.snapshot.BalanceExternalSnapshot;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Баланс → двухуровневый снапшот — группа `U17` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Balance.md §«`BalanceOkxResponse` → snapshot»).
 *
 * <p><b>Базовая сборка:</b> {@code BalanceOkxResponse} с тремя
 * равновесными величинами и списком из одной валютной записи.
 *
 * <p><b>Числа остаются строками — и в снапшоте, и на уровне валюты.</b>
 * Перевод в числа происходит на следующем переходе, при сборке
 * доменного контейнера; кейсы поэтому проверяют, что снапшот ничего не
 * разбирает, кроме времени.
 */
class BalanceToSnapshotTest {

    private final BalanceContainerMapper mapper = Mappers.balance();

    @Test
    @DisplayName("U17.1 — контейнер: момент разобран, три величины остались строками")
    void u17_1_theContainerParsesOnlyTheMoment() {
        BalanceContainerExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.balance());

        assertThat(snapshot.getExternalUpdatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(snapshot.getExternalTotalEquity()).isEqualTo("1000");
        assertThat(snapshot.getExternalAdjustedEquity()).isEqualTo("1000");
        assertThat(snapshot.getExternalAvailableEquity()).isEqualTo("900");
        assertThat(snapshot.getBalances()).hasSize(1);
    }

    @Test
    @DisplayName("U17.2 — валютный снапшот: момент разобран, четыре величины строками")
    void u17_2_theCurrencyLevelParsesOnlyTheMoment() {
        BalanceExternalSnapshot currency =
                mapper.integrationToSnapshot(OkxFixture.balance()).getBalances().getFirst();

        assertThat(currency.getExternalCurrency()).isEqualTo("USDT");
        assertThat(currency.getExternalUpdatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
        assertThat(currency.getExternalEquity()).isEqualTo("900");
        assertThat(currency.getExternalCashBalance()).isEqualTo("800");
        assertThat(currency.getExternalAvailableBalance()).isEqualTo("700");
        assertThat(currency.getExternalFrozenBalance()).isEqualTo("100");
    }

    /** Непустоту мерит структурная валидация читателя. */
    @Test
    @DisplayName("U17.3 — пустой список валют остаётся пустым списком, отказа нет")
    void u17_3_anEmptyCurrencyListStaysAnEmptyList() {
        BalanceOkxResponse response = OkxFixture.balance();
        response.setDetails(List.of());

        assertThat(mapper.integrationToSnapshot(response).getBalances()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U17.4 — отсутствие списка валют даёт пустоту")
    void u17_4_anAbsentCurrencyListIsEmptiness() {
        BalanceOkxResponse response = OkxFixture.balance();
        response.setDetails(null);

        assertThat(mapper.integrationToSnapshot(response).getBalances()).isNull();
    }

    @Test
    @DisplayName("U17.5 — три валюты в порядке ответа")
    void u17_5_threeCurrenciesKeepTheirOrder() {
        BalanceOkxResponse response = OkxFixture.balance();
        response.setDetails(List.of(OkxFixture.balanceDetail("USDT"),
                OkxFixture.balanceDetail("BTC"),
                OkxFixture.balanceDetail("ETH")));

        assertThat(mapper.integrationToSnapshot(response).getBalances())
                .extracting(BalanceExternalSnapshot::getExternalCurrency)
                .containsExactly("USDT", "BTC", "ETH");
    }

    /** Два разных момента, и путать их нельзя. */
    @Test
    @DisplayName("U17.6 — пустой момент контейнера не затрагивает момент валютной записи")
    void u17_6_theTwoMomentsAreIndependent() {
        BalanceOkxResponse response = OkxFixture.balance();
        response.setuTime("");

        BalanceContainerExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getExternalUpdatedAt()).isNull();
        assertThat(snapshot.getBalances().getFirst().getExternalUpdatedAt())
                .isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
    }

    /** Перевода здесь нет вовсе: пустота появится на следующем переходе. */
    @Test
    @DisplayName("U17.7 — пустая равновесная величина остаётся пустой строкой")
    void u17_7_anEmptyEquityStaysAnEmptyString() {
        BalanceOkxResponse response = OkxFixture.balance();
        response.setTotalEq("");

        assertThat(mapper.integrationToSnapshot(response).getExternalTotalEquity())
                .isNotNull().isEmpty();
    }

    /** Счёта коннектор не знает: его проставляет ядро, приземляя снимок. */
    @Test
    @DisplayName("U17.8 — поля счёта у снимка нет вовсе")
    void u17_8_theSnapshotCarriesNoAccountField() {
        assertThat(BalanceContainerExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("exchangeAccountId", "accountId");
    }

    @Test
    @DisplayName("U17.9 — пустота вместо формы источника даёт пустоту на обоих уровнях")
    void u17_9_emptinessInIsEmptinessOutOnBothLevels() {
        assertThat(mapper.integrationToSnapshot((BalanceOkxResponse) null)).isNull();
        assertThat(mapper.integrationToSnapshot((BalanceDetailOkxResponse) null)).isNull();
    }
}
