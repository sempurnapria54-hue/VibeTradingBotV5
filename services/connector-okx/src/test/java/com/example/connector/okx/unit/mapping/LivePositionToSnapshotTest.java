package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.PositionOkxResponse;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.Position;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Живая позиция → граничный снапшот — группа `U11` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Position.md §«`PositionOkxResponse` →
 * `PositionExternalSnapshot`»).
 *
 * <p><b>Базовая сборка:</b> {@code PositionOkxResponse} с непустыми
 * идентичностью, знаковым размером, пятью числами, двумя моментами и
 * четырьмя инвариантными операндами.
 */
class LivePositionToSnapshotTest {

    private final PositionMapper mapper = Mappers.position();

    @Test
    @DisplayName("U11.1 — базовая сборка: длинная нога, пять чисел и два момента")
    void u11_1_theBaseAssemblyLandsFieldByField() {
        PositionExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.position());

        assertThat(snapshot.getExternalId()).isEqualTo("p1");
        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getExternalSize()).isEqualByComparingTo("5");
        assertThat(snapshot.getDirection()).isEqualTo(Position.Direction.LONG);
        assertThat(snapshot.getExternalAverageEntryPrice()).isEqualByComparingTo("100");
        assertThat(snapshot.getExternalMarkPrice()).isEqualByComparingTo("101");
        assertThat(snapshot.getExternalLiquidationPrice()).isEqualByComparingTo("50");
        assertThat(snapshot.getExternalMargin()).isEqualByComparingTo("20");
        assertThat(snapshot.getExternalUnrealizedProfit()).isEqualByComparingTo("5");
        assertThat(snapshot.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(snapshot.getExternalModifiedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
    }

    /** Одно поле источника даёт два поля снапшота. */
    @Test
    @DisplayName("U11.2 — короткая нога: модуль в размер, знак в направление")
    void u11_2_aShortLegSplitsIntoModulusAndDirection() {
        PositionOkxResponse response = OkxFixture.position();
        response.setPos("-5");

        PositionExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getExternalSize()).isEqualByComparingTo("5");
        assertThat(snapshot.getDirection()).isEqualTo(Position.Direction.SHORT);
    }

    /** Нулевая нога встречается на тропе закрытия: подставленное направление стало бы наблюдением. */
    @Test
    @DisplayName("U11.3 — нулевая нога: размер ноль, направления нет")
    void u11_3_aZeroLegHasSizeButNoDirection() {
        PositionOkxResponse response = OkxFixture.position();
        response.setPos("0");

        PositionExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getExternalSize()).isEqualByComparingTo("0");
        assertThat(snapshot.getDirection()).isNull();
    }

    @Test
    @DisplayName("U11.4 — пустая нога: пусты и размер, и направление")
    void u11_4_anEmptyLegHasNeitherSizeNorDirection() {
        PositionOkxResponse response = OkxFixture.position();
        response.setPos("");

        PositionExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getExternalSize()).isNull();
        assertThat(snapshot.getDirection()).isNull();
    }

    /** Инвариантные операнды: полей под них у снапшота нет. */
    @Test
    @DisplayName("U11.5 — четыре инвариантных операнда в снапшот не переносятся")
    void u11_5_fourInvariantOperandsDoNotCrossTheBoundary() {
        assertThat(PositionExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("instType", "posSide", "mgnMode", "lever",
                        "externalInstrumentType", "externalPositionSide", "externalMarginMode",
                        "externalLeverage");
    }

    /** Ноль здесь означал бы мгновенную ликвидацию. */
    @Test
    @DisplayName("U11.6 — недобытая цена ликвидации остаётся пустотой")
    void u11_6_anAbsentLiquidationPriceIsEmptiness() {
        PositionOkxResponse response = OkxFixture.position();
        response.setLiqPx("");

        assertThat(mapper.integrationToSnapshot(response).getExternalLiquidationPrice()).isNull();
    }

    /** Конвенции издержки к результату не применяются. */
    @Test
    @DisplayName("U11.7 — знак нереализованного результата сырой")
    void u11_7_theUnrealizedProfitSignStaysRaw() {
        PositionOkxResponse response = OkxFixture.position();
        response.setUpl("-12.5");

        assertThat(mapper.integrationToSnapshot(response).getExternalUnrealizedProfit())
                .isEqualByComparingTo("-12.5");
    }

    /** Срез по счёту возвращает строки многих инструментов сразу. */
    @Test
    @DisplayName("U11.8 — снапшот адресуется инструментом")
    void u11_8_theSnapshotIsAddressedByInstrument() {
        assertThat(mapper.integrationToSnapshot(OkxFixture.position()).getExternalInstrumentId())
                .isEqualTo("ETH-USDT-SWAP");
    }

    @Test
    @DisplayName("U11.9 — пустота вместо формы источника даёт пустоту")
    void u11_9_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot(null)).isNull();
    }
}
