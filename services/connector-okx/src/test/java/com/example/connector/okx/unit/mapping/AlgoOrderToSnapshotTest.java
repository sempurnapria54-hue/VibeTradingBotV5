package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.response.AlgoOrderOkxResponse;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.snapshot.AlgoOrderExternalSnapshot;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Условная заявка → снапшот: плоские поля в дерево условия — группа
 * `U10` документа `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/AlgoOrder.md §«`AlgoOrderOkxResponse` →
 * `AlgoOrderExternalSnapshot`»).
 *
 * <p><b>Базовая сборка:</b> {@code AlgoOrderOkxResponse} с непустыми
 * плоскими полями и полной защитной парой, ценой активации и текущим
 * уровнем трейлинга.
 *
 * <p><b>Переход написан руками, и это объявлено:</b> площадка отдаёт
 * условие плоскими полями, а снапшот держит дерево, которое перенос по
 * именам не собирает. Отсюда у группы обязательны кейсы на вырождения
 * дерева: ветка, у которой нет ни одной ноги, обязана быть пустой, а не
 * пустым объектом.
 */
class AlgoOrderToSnapshotTest {

    private final AlgoOrderMapper mapper = Mappers.algoOrder();

    @Test
    @DisplayName("U10.1 — плоские поля базовой сборки")
    void u10_1_theFlatFieldsLandFieldByField() {
        AlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.algoOrder());

        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(snapshot.getInternalId()).isEqualTo("tb-9");
        assertThat(snapshot.getExternalId()).isEqualTo("9");
        assertThat(snapshot.getExternalStatus()).isEqualTo("live");
        assertThat(snapshot.getFailCode()).isEqualTo("0");
        assertThat(snapshot.getExternalSize()).isEqualByComparingTo("3");
        assertThat(snapshot.getExternalPrice()).isEqualByComparingTo("99");
        assertThat(snapshot.getExternalTriggerTime()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(snapshot.getLinkedOrderExternalIds()).containsExactly("1", "2");
        assertThat(snapshot.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(snapshot.getExternalModifiedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
    }

    @Test
    @DisplayName("U10.2 — дерево условия собрано из плоских полей")
    void u10_2_theConditionTreeIsAssembled() {
        AlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.algoOrder());

        assertThat(snapshot.getCondition().getTrigger().getStopLoss().getExternalType()).isEqualTo("mark");
        assertThat(snapshot.getCondition().getTrigger().getStopLoss().getExternalValue())
                .isEqualByComparingTo("90");
        assertThat(snapshot.getCondition().getTrigger().getTakeProfit().getExternalType()).isEqualTo("last");
        assertThat(snapshot.getCondition().getTrigger().getTakeProfit().getExternalValue())
                .isEqualByComparingTo("120");
        assertThat(snapshot.getCondition().getTrailing().getActivationPrice().getExternalType()).isNull();
        assertThat(snapshot.getCondition().getTrailing().getActivationPrice().getExternalValue())
                .isEqualByComparingTo("110");
        assertThat(snapshot.getCondition().getTrailing().getExternalPrice()).isEqualByComparingTo("95");
    }

    /** Нога, у которой нет ни типа, ни значения, не существует. */
    @Test
    @DisplayName("U10.3 — обе половины ноги пусты: ноги нет")
    void u10_3_aLegWithNeitherHalfDoesNotExist() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setSlTriggerPx("");
        response.setSlTriggerPxType("");

        assertThat(mapper.integrationToSnapshot(response).getCondition().getTrigger().getStopLoss())
                .isNull();
    }

    /** Достаточно одной непустой половины; строковая половина переносится как есть (`Z2`). */
    @Test
    @DisplayName("U10.4 — непуст только уровень: нога есть")
    void u10_4_aLegWithOnlyAValueExists() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setSlTriggerPx("90");
        response.setSlTriggerPxType("");

        var leg = mapper.integrationToSnapshot(response).getCondition().getTrigger().getStopLoss();

        assertThat(leg).isNotNull();
        assertThat(leg.getExternalValue()).isEqualByComparingTo("90");
        assertThat(leg.getExternalType()).isEmpty();
    }

    @Test
    @DisplayName("U10.5 — непуст только тип: нога есть")
    void u10_5_aLegWithOnlyATypeExists() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setSlTriggerPx("");
        response.setSlTriggerPxType("mark");

        var leg = mapper.integrationToSnapshot(response).getCondition().getTrigger().getStopLoss();

        assertThat(leg).isNotNull();
        assertThat(leg.getExternalType()).isEqualTo("mark");
        assertThat(leg.getExternalValue()).isNull();
    }

    /** Ветка строится всегда, пустеют только её ноги. */
    @Test
    @DisplayName("U10.6 — обе ноги пусты: ветка есть, ноги пусты")
    void u10_6_theTriggerBranchIsBuiltEvenWithoutLegs() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setSlTriggerPx("");
        response.setSlTriggerPxType("");
        response.setTpTriggerPx("");
        response.setTpTriggerPxType("");

        var trigger = mapper.integrationToSnapshot(response).getCondition().getTrigger();

        assertThat(trigger).isNotNull();
        assertThat(trigger.getStopLoss()).isNull();
        assertThat(trigger.getTakeProfit()).isNull();
    }

    @Test
    @DisplayName("U10.7 — трейлинг пуст: ветка есть, цены активации нет")
    void u10_7_theTrailingBranchIsBuiltEvenWhenEmpty() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setActivePx("");
        response.setMoveTriggerPx("");

        var trailing = mapper.integrationToSnapshot(response).getCondition().getTrailing();

        assertThat(trailing).isNotNull();
        assertThat(trailing.getActivationPrice()).isNull();
        assertThat(trailing.getExternalPrice()).isNull();
    }

    /** Тип цены активации источник не отдаёт, и это не нарушение инварианта. */
    @Test
    @DisplayName("U10.8 — у цены активации типа нет всегда")
    void u10_8_theActivationPriceNeverCarriesAType() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setActivePx("110");

        var activation = mapper.integrationToSnapshot(response).getCondition().getTrailing()
                .getActivationPrice();

        assertThat(activation.getExternalValue()).isEqualByComparingTo("110");
        assertThat(activation.getExternalType()).isNull();
    }

    /** Текущий уровень трейлинга и цена срабатывания — два разных факта. */
    @Test
    @DisplayName("U10.9 — уровень трейлинга есть, цены срабатывания нет")
    void u10_9_theTrailingLevelIsNotTheTriggeredPrice() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setMoveTriggerPx("95");
        response.setActualPx("");

        AlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getCondition().getTrailing().getExternalPrice()).isEqualByComparingTo("95");
        assertThat(snapshot.getExternalPrice()).isNull();
    }

    @Test
    @DisplayName("U10.10 — обратная пара: цена срабатывания есть, уровня трейлинга нет")
    void u10_10_theTriggeredPriceIsNotTheTrailingLevel() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setActualPx("99");
        response.setMoveTriggerPx("");

        AlgoOrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getExternalPrice()).isEqualByComparingTo("99");
        assertThat(snapshot.getCondition().getTrailing().getExternalPrice()).isNull();
    }

    @Test
    @DisplayName("U10.11 — пустой список связанных заявок остаётся пустым списком")
    void u10_11_anEmptyLinkedListStaysAnEmptyList() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setOrdIdList(List.of());

        assertThat(mapper.integrationToSnapshot(response).getLinkedOrderExternalIds())
                .isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U10.12 — отсутствие списка связанных заявок даёт пустоту")
    void u10_12_anAbsentLinkedListIsEmptiness() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setOrdIdList(null);

        assertThat(mapper.integrationToSnapshot(response).getLinkedOrderExternalIds()).isNull();
    }

    /** У живой защиты срабатывания ещё не было. */
    @Test
    @DisplayName("U10.13 — пустое время срабатывания даёт пустоту")
    void u10_13_anEmptyTriggerTimeIsEmptiness() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setTriggerTime("");

        assertThat(mapper.integrationToSnapshot(response).getExternalTriggerTime()).isNull();
    }

    /** В снапшот они не попадают по построению, а не решением маппера. */
    @Test
    @DisplayName("U10.14 — пяти полей запроса у самой формы источника нет")
    void u10_14_fiveRequestFieldsAreAbsentFromTheSourceForm() {
        assertThat(AlgoOrderOkxResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("ordType", "side", "tdMode", "posSide", "reduceOnly");
    }

    /** Охрана написана явно, потому что метод не порождается. */
    @Test
    @DisplayName("U10.15 — пустота вместо формы источника даёт пустоту")
    void u10_15_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot(null)).isNull();
    }

    @Test
    @DisplayName("U10.16 — время обновления у живой ноги цикла может не приходить")
    void u10_16_anEmptyModifiedTimeIsEmptiness() {
        AlgoOrderOkxResponse response = OkxFixture.algoOrder();
        response.setuTime("");

        assertThat(mapper.integrationToSnapshot(response).getExternalModifiedAt()).isNull();
    }
}
