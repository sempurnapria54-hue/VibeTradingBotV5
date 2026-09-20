package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.integration.external.api.model.okx.response.OrderOkxResponse;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.snapshot.OrderExternalSnapshot;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Заявка площадки → граничный снапшот — группа `U7` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Order.md §«`OrderOkxResponse` →
 * `OrderExternalSnapshot`»: таблица переноса полей и перечень того,
 * чего в снапшоте нет намеренно).
 *
 * <p><b>Базовая сборка:</b> {@code OrderOkxResponse} со всеми
 * непустыми полями, встроенной защиты нет; порождённая реализация
 * {@code OrderMapper}, конвертер настоящий.
 */
class OrderToSnapshotTest {

    private final OrderMapper mapper = Mappers.order();

    @Test
    @DisplayName("U7.1 — базовая сборка: идентичности, литералы, четыре числа и два момента")
    void u7_1_theBaseAssemblyLandsFieldByField() {
        OrderExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.order());

        assertThat(snapshot.getInternalId()).isEqualTo("tb-1");
        assertThat(snapshot.getExternalId()).isEqualTo("1");
        assertThat(snapshot.getType()).isEqualTo("limit");
        assertThat(snapshot.getSide()).isEqualTo("buy");
        assertThat(snapshot.getExternalStatus()).isEqualTo("live");
        assertThat(snapshot.getPrice()).isEqualByComparingTo("100");
        assertThat(snapshot.getSize()).isEqualByComparingTo("2");
        assertThat(snapshot.getAccumulatedFillSize()).isEqualByComparingTo("1");
        assertThat(snapshot.getAveragePrice()).isEqualByComparingTo("99.5");
        assertThat(snapshot.getExternalCreatedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));
        assertThat(snapshot.getExternalModifiedAt()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:14:20Z"));
        assertThat(snapshot.getExternalInstrumentId()).isEqualTo(OkxFixture.INSTRUMENT);
    }

    /** Пустой список означал бы наблюдение «защиты нет», которого не было. */
    @Test
    @DisplayName("U7.2 — встроенной защиты нет: пустота, а не пустой список")
    void u7_2_absentProtectionIsEmptinessNotAnEmptyList() {
        OrderExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.order());

        assertThat(snapshot.getAttachedAlgoOrders()).isNull();
    }

    @Test
    @DisplayName("U7.3 — рыночная заявка: цена пуста, прочие числа не затронуты")
    void u7_3_aMarketOrderHasNoPrice() {
        OrderOkxResponse response = OkxFixture.order();
        response.setPx("");

        OrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getPrice()).isNull();
        assertThat(snapshot.getSize()).isEqualByComparingTo("2");
        assertThat(snapshot.getAccumulatedFillSize()).isEqualByComparingTo("1");
        assertThat(snapshot.getAveragePrice()).isEqualByComparingTo("99.5");
    }

    /** Нулевой налив есть наблюдённый факт, а пустой — недобытый. */
    @Test
    @DisplayName("U7.4 — нулевой налив есть ноль, а не пустота")
    void u7_4_aZeroFillIsZeroNotEmptiness() {
        OrderOkxResponse response = OkxFixture.order();
        response.setAccFillSz("0");

        assertThat(mapper.integrationToSnapshot(response).getAccumulatedFillSize()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U7.5 — средней цены у неисполненной заявки не существует")
    void u7_5_anUnfilledOrderHasNoAveragePrice() {
        OrderOkxResponse response = OkxFixture.order();
        response.setAvgPx("");
        response.setAccFillSz("0");

        assertThat(mapper.integrationToSnapshot(response).getAveragePrice()).isNull();
    }

    /** Перевод в доменный перечень происходит на следующем переходе. */
    @Test
    @DisplayName("U7.6 — сторона лежит в снапшоте литералом источника")
    void u7_6_theSideStaysASourceLiteral() {
        OrderOkxResponse response = OkxFixture.order();
        response.setSide("sell");

        assertThat(mapper.integrationToSnapshot(response).getSide()).isEqualTo("sell");
    }

    /** Резолвер зовёт шлюз последним шагом чтения. */
    @Test
    @DisplayName("U7.7 — доменного статуса в снапшоте нет вовсе")
    void u7_7_thereIsNoDomainStatusInTheSnapshot() {
        OrderOkxResponse response = OkxFixture.order();
        response.setState("filled");

        assertThat(mapper.integrationToSnapshot(response).getExternalStatus()).isEqualTo("filled");
        assertThat(OrderExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("status");
    }

    /** Идентичность защиты живёт на элементе списка. */
    @Test
    @DisplayName("U7.8 — клиентский идентификатор защиты живёт только в снапшоте")
    void u7_8_theAttachedClientIdLivesOnlyInTheSnapshot() {
        assertThat(mapper.integrationToSnapshot(OkxFixture.order()).getAttachedAlgoInternalId())
                .isEqualTo("tb-p1");
    }

    /** У доменной заявки уровень живёт на элементе защиты. */
    @Test
    @DisplayName("U7.9 — оба верхнеуровневых триггера переносятся только в снапшот")
    void u7_9_bothTopLevelTriggersLandOnlyInTheSnapshot() {
        OrderExternalSnapshot snapshot = mapper.integrationToSnapshot(OkxFixture.order());

        assertThat(snapshot.getTakeProfitTriggerPrice()).isEqualByComparingTo("120");
        assertThat(snapshot.getStopLossTriggerPrice()).isEqualByComparingTo("90");
    }

    /** Сверка намерения — отдельный ход, и писателя у неё нет. */
    @Test
    @DisplayName("U7.10 — признака «только уменьшать» у формы источника нет вовсе")
    void u7_10_theSourceFormCarriesNoReduceOnly() {
        assertThat(OrderOkxResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("reduceOnly");
        assertThat(OrderExternalSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("reduceOnly", "positionReducingOnly");
    }

    /** Структурной валидации маппер не делает: она у читателя. */
    @Test
    @DisplayName("U7.11 — все поля пусты, кроме клиентского идентификатора: отказа нет")
    void u7_11_anAlmostEmptyResponseStillBuildsASnapshot() {
        OrderOkxResponse response = new OrderOkxResponse();
        response.setClOrdId("tb-1");

        OrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getInternalId()).isEqualTo("tb-1");
        assertThat(snapshot.getExternalId()).isNull();
        assertThat(snapshot.getType()).isNull();
        assertThat(snapshot.getSide()).isNull();
        assertThat(snapshot.getExternalStatus()).isNull();
        assertThat(snapshot.getPrice()).isNull();
        assertThat(snapshot.getSize()).isNull();
        assertThat(snapshot.getExternalCreatedAt()).isNull();
        assertThat(snapshot.getExternalInstrumentId()).isNull();
    }

    @Test
    @DisplayName("U7.12 — пустота вместо формы источника даёт пустоту")
    void u7_12_emptinessInIsEmptinessOut() {
        assertThat(mapper.integrationToSnapshot((OrderOkxResponse) null)).isNull();
    }

    /** Конвенция комиссии как издержки снимается у ставки прогноза, а не у факта заявки. */
    @Test
    @DisplayName("U7.13 — знак комиссии остаётся сырым")
    void u7_13_theFeeSignStaysRaw() {
        assertThat(mapper.integrationToSnapshot(OkxFixture.order()).getFee()).isEqualByComparingTo("-0.25");
    }

    @Test
    @DisplayName("U7.14 — неразбираемое число роняет переход, а не пустеет")
    void u7_14_anUnparseableNumberBreaksTheTransition() {
        OrderOkxResponse response = OkxFixture.order();
        response.setPx("abc");

        assertThatThrownBy(() -> mapper.integrationToSnapshot(response))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("U7.15 — список встроенной защиты из двух элементов, порядок сохранён")
    void u7_15_theProtectionListKeepsItsOrder() {
        OrderOkxResponse response = OkxFixture.order();
        var first = OkxFixture.attachedInParentBody();
        var second = OkxFixture.attachedInParentBody();
        second.setAttachAlgoClOrdId("tb-p2");
        second.setSlTriggerPx("80");
        response.setAttachAlgoOrds(List.of(first, second));

        OrderExternalSnapshot snapshot = mapper.integrationToSnapshot(response);

        assertThat(snapshot.getAttachedAlgoOrders()).hasSize(2);
        assertThat(snapshot.getAttachedAlgoOrders().getFirst().getInternalId()).isEqualTo("tb-p1");
        assertThat(snapshot.getAttachedAlgoOrders().getFirst().getStopLossTriggerPrice())
                .isEqualByComparingTo("90");
        assertThat(snapshot.getAttachedAlgoOrders().getLast().getInternalId()).isEqualTo("tb-p2");
        assertThat(snapshot.getAttachedAlgoOrders().getLast().getStopLossTriggerPrice())
                .isEqualByComparingTo("80");
    }
}
