package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.exception.ExternalInvariantViolationException;
import com.example.connector.okx.integration.external.api.model.okx.response.PositionsHistoryOkxResponse;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.snapshot.PositionCloseResultExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.Position;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Запись закрытия позиции → граничный снапшот — группа `U12`
 * документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/PositionCloseResult.md §«Граничный снапшот» и
 * §«Знак финансирования нормализуется здесь, и только здесь»).
 *
 * <p><b>Базовая сборка:</b> {@code PositionsHistoryOkxResponse} со
 * всеми непустыми полями, направление — длинное.
 *
 * <p><b>Четыре слагаемых тождества переводятся по несобытийной природе,
 * и конвенции у них три, а не одна:</b> пустое значение становится
 * нулём — величина существует всегда; финансирование сверх того меняет
 * знак — домен хранит его издержкой; комиссия и штраф остаются сырыми —
 * они сравниваются с сырыми же суммами разбивки. Смешение конвенций
 * даёт расхождение на всей популяции сразу.
 */
class PositionCloseRecordToSnapshotTest {

    private final PositionMapper mapper = Mappers.position();

    @Test
    @DisplayName("U12.1 — базовая сборка: перенос, доменное направление и два момента")
    void u12_1_theBaseAssemblyLandsFieldByField() {
        PositionCloseResultExternalSnapshot snapshot =
                mapper.integrationToCloseSnapshot(OkxFixture.positionHistory());

        assertThat(snapshot.getExternalPosId()).isEqualTo("p1");
        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getExternalRealizedPnl()).isEqualByComparingTo("10");
        assertThat(snapshot.getExternalResultCurrency()).isEqualTo("USDT");
        assertThat(snapshot.getExternalCloseAveragePrice()).isEqualByComparingTo("105");
        assertThat(snapshot.getExternalCloseType()).isEqualTo("1");
        assertThat(snapshot.getDirection()).isEqualTo(Position.Direction.LONG);
        assertThat(snapshot.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(snapshot.getExternalModifiedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
    }

    @Test
    @DisplayName("U12.2 — результат до издержек переносится числом")
    void u12_2_theGrossResultIsCarried() {
        assertThat(mapper.integrationToCloseSnapshot(OkxFixture.positionHistory())
                .getExternalRealizedPnlGross()).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("U12.3 — пустой результат до издержек есть ноль, не пустота")
    void u12_3_anEmptyGrossResultIsZero() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setPnl("");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalRealizedPnlGross())
                .isEqualByComparingTo("0");
    }

    /** У комиссии и штрафа ликвидации знак остаётся сырым. */
    @Test
    @DisplayName("U12.4 — знак комиссии сырой")
    void u12_4_theFeeSignStaysRaw() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setFee("-0.3");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalFee())
                .isEqualByComparingTo("-0.3");
    }

    @Test
    @DisplayName("U12.5 — пустая комиссия есть ноль")
    void u12_5_anEmptyFeeIsZero() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setFee("");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalFee()).isEqualByComparingTo("0");
    }

    /** Уплаченное фондирование кладётся издержкой. */
    @Test
    @DisplayName("U12.6 — уплаченное фондирование становится положительной издержкой")
    void u12_6_paidFundingBecomesAPositiveCost() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setFundingFee("-2");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalFundingCost())
                .isEqualByComparingTo("2");
    }

    /** Умножение на минус единицу, а не модуль: различимость сохранена. */
    @Test
    @DisplayName("U12.7 — полученное фондирование становится отрицательной издержкой")
    void u12_7_receivedFundingBecomesANegativeCost() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setFundingFee("2");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalFundingCost())
                .isEqualByComparingTo("-2");
    }

    @Test
    @DisplayName("U12.8 — пустое фондирование есть ноль, и тождество сходится")
    void u12_8_anEmptyFundingIsZero() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setFundingFee("");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalFundingCost())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U12.9 — знак штрафа ликвидации сырой")
    void u12_9_theLiquidationPenaltySignStaysRaw() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setLiqPenalty("-5");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalLiquidationPenalty())
                .isEqualByComparingTo("-5");
    }

    @Test
    @DisplayName("U12.10 — пустой штраф ликвидации есть ноль")
    void u12_10_anEmptyLiquidationPenaltyIsZero() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setLiqPenalty("");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalLiquidationPenalty())
                .isEqualByComparingTo("0");
    }

    /** Конвертация направления идёт ДО проверки обязательности контракта записи. */
    @Test
    @DisplayName("U12.11 — пустое направление отказывает")
    void u12_11_anEmptyDirectionRefuses() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setDirection("");

        assertThatThrownBy(() -> mapper.integrationToCloseSnapshot(response))
                .isInstanceOf(ExternalInvariantViolationException.class)
                .hasMessageContaining("эпизод не материализуем");
    }

    @Test
    @DisplayName("U12.12 — направление вне перечня отказывает, значение в сообщении")
    void u12_12_anUnknownDirectionRefusesWithItsValue() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setDirection("net");

        assertThatThrownBy(() -> mapper.integrationToCloseSnapshot(response))
                .isInstanceOf(ExternalInvariantViolationException.class)
                .hasMessageContaining("net");
    }

    /** Тип закрытия в контракт границы не входит: его непригодность обрабатывает резолв исхода. */
    @Test
    @DisplayName("U12.13 — пустой тип закрытия: пустая строка, отказа нет")
    void u12_13_anEmptyCloseTypeIsAnEmptyStringNotARefusal() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setType("");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalCloseType())
                .isNotNull().isEmpty();
    }

    /** Сырое значение персистируется всегда, чтобы перечень нераспознанного собирался по данным. */
    @Test
    @DisplayName("U12.14 — тип закрытия вне перечня переносится строкой")
    void u12_14_anUnknownCloseTypeIsCarriedAsAString() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setType("9");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalCloseType()).isEqualTo("9");
    }

    /** Готовый результат биржи под несобытийную конвенцию не попадает. */
    @Test
    @DisplayName("U12.15 — пустой готовый результат остаётся пустотой")
    void u12_15_anEmptyReadyResultStaysEmptiness() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setRealizedPnl("");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalRealizedPnl()).isNull();
    }

    /** Непустоту мерит структурная валидация читателя. */
    @Test
    @DisplayName("U12.16 — пустая валюта результата: пустая строка, отказа нет")
    void u12_16_anEmptyResultCurrencyIsAnEmptyString() {
        PositionsHistoryOkxResponse response = OkxFixture.positionHistory();
        response.setCcy("");

        assertThat(mapper.integrationToCloseSnapshot(response).getExternalResultCurrency())
                .isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U12.17 — пустота вместо формы источника даёт пустоту")
    void u12_17_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToCloseSnapshot(null)).isNull();
    }
}
