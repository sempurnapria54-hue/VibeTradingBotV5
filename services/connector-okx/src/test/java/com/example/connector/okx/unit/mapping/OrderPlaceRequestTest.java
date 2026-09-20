package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.connector.okx.integration.external.api.model.okx.request.PlaceOrderOkxRequest;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Доменная заявка → запрос постановки — группа `U23` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Order.md §«`Domain Order → request`» и
 * §«`Domain Order → OKX request`»).
 *
 * <p><b>Базовая сборка:</b> доменная {@code Order} с клиентским
 * идентификатором, стороной покупки, размером, ценой и ложным
 * признаком «только уменьшать»; встроенной защиты нет, имя инструмента
 * площадки — вторым аргументом.
 *
 * <p><b>Три поля запроса — adapter-константы, и в домене их нет:</b>
 * режим маржи, сторона позиции и метка запроса ставятся здесь, из
 * констант источника.
 */
class OrderPlaceRequestTest {

    private final OrderMapper mapper = Mappers.order();

    private static Order order() {
        Order order = new Order();
        order.setInternalId("tb-1");
        order.setSide(Order.Side.BUY);
        order.setSize(BigDecimal.ONE);
        order.setPrice(new BigDecimal("100"));
        order.setPositionReducingOnly(false);
        return order;
    }

    private static AttachedAlgoOrder attached() {
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setInternalId("tb-p1");
        attached.setStopLossTriggerPrice(new BigDecimal("90"));
        attached.setTriggerPriceType(
                com.example.tradingbot.domain.model.core.algo_order.AlgoOrder.TriggerPriceType.MARK);
        return attached;
    }

    @Test
    @DisplayName("U23.1 — базовая сборка: три adapter-константы и перенос доменных величин")
    void u23_1_theBaseAssemblyBuildsTheRequest() {
        PlaceOrderOkxRequest request = mapper.domainToPlaceRequest(order(), OkxFixture.INSTRUMENT);

        assertThat(request.getInstId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(request.getTdMode()).isEqualTo("isolated");
        assertThat(request.getPosSide()).isEqualTo("net");
        assertThat(request.getTag()).isEqualTo("tb");
        assertThat(request.getClOrdId()).isEqualTo("tb-1");
        assertThat(request.getSide()).isEqualTo("buy");
        assertThat(request.getSz()).isEqualTo("1");
        assertThat(request.getPx()).isEqualTo("100");
        assertThat(request.getReduceOnly()).isFalse();
        assertThat(request.getOrdType()).isEqualTo("limit");
    }

    /** Тип исполнения выводится из наличия цены, а не из отдельного поля. */
    @Test
    @DisplayName("U23.2 — заявка без цены становится рыночной")
    void u23_2_anOrderWithoutAPriceIsMarket() {
        Order order = order();
        order.setPrice(null);

        PlaceOrderOkxRequest request = mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT);

        assertThat(request.getOrdType()).isEqualTo("market");
        assertThat(request.getPx()).isNull();
    }

    /** Признак — наличие цены, а не её значение. */
    @Test
    @DisplayName("U23.3 — нулевая цена оставляет заявку лимитной")
    void u23_3_aZeroPriceStaysLimit() {
        Order order = order();
        order.setPrice(BigDecimal.ZERO);

        PlaceOrderOkxRequest request = mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT);

        assertThat(request.getOrdType()).isEqualTo("limit");
        assertThat(request.getPx()).isEqualTo("0");
    }

    @Test
    @DisplayName("U23.4 — доменная продажа в словарь площадки")
    void u23_4_aSellSideBecomesTheSourceWord() {
        Order order = order();
        order.setSide(Order.Side.SELL);

        assertThat(mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT).getSide())
                .isEqualTo("sell");
    }

    /** Округление под шаг делает расчётный слой. */
    @Test
    @DisplayName("U23.5 — масштаб размера сохранён")
    void u23_5_theSizeScaleIsKept() {
        Order order = order();
        order.setSize(new BigDecimal("1.500"));

        assertThat(mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT).getSz())
                .isEqualTo("1.500");
    }

    @Test
    @DisplayName("U23.6 — экспоненциальный размер пишется плоско")
    void u23_6_anExponentialSizeIsWrittenPlain() {
        Order order = order();
        order.setSize(new BigDecimal("1E-3"));

        assertThat(mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT).getSz())
                .isEqualTo("0.001");
    }

    /** Сверки эха у этого поля нет, и это названный остаток. */
    @Test
    @DisplayName("U23.7 — намерение «только уменьшать» переносится истиной")
    void u23_7_theReduceOnlyIntentIsCarried() {
        Order order = order();
        order.setPositionReducingOnly(true);

        assertThat(mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT).getReduceOnly()).isTrue();
    }

    /** В тело ключ не уходит по аннотации формы (звено `Z3`): площадка применит своё умолчание. */
    @Test
    @DisplayName("U23.8 — пустое намерение оставляет поле запроса пустым")
    void u23_8_anEmptyIntentLeavesTheFieldEmpty() {
        Order order = order();
        order.setPositionReducingOnly(null);

        assertThat(mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT).getReduceOnly()).isNull();
    }

    /** Пустой список означал бы «защита объявлена и пуста». */
    @Test
    @DisplayName("U23.9 — защиты нет: пустота, а не пустой список")
    void u23_9_absentProtectionIsEmptinessNotAnEmptyList() {
        assertThat(mapper.domainToPlaceRequest(order(), OkxFixture.INSTRUMENT).getAttachAlgoOrds())
                .isNull();
    }

    @Test
    @DisplayName("U23.10 — встроенная защита уезжает списком из одного запроса")
    void u23_10_protectionTravelsAsAListOfOne() {
        Order order = order();
        order.setAttachedAlgoOrders(List.of(attached()));

        PlaceOrderOkxRequest request = mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT);

        assertThat(request.getAttachAlgoOrds()).hasSize(1);
        assertThat(request.getAttachAlgoOrds().getFirst().getAttachAlgoClOrdId()).isEqualTo("tb-p1");
        assertThat(request.getAttachAlgoOrds().getFirst().getSlTriggerPx()).isEqualTo("90");
    }

    /** Они описаны как поверхность источника и доменом не используются. */
    @Test
    @DisplayName("U23.11 — трёх полей поверхности источника в запросе нет")
    void u23_11_threeSourceSurfaceFieldsAreAbsent() {
        assertThat(PlaceOrderOkxRequest.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("ccy", "stpMode", "expTime");
    }

    /** Домен не амендит: ремоделирование есть REPLACE-оркестрация. */
    @Test
    @DisplayName("U23.12 — амендных полей в запросе нет ни одного")
    void u23_12_thereAreNoAmendFields() {
        assertThat(PlaceOrderOkxRequest.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("newSz", "newPx", "reqId", "newTpTriggerPx", "newSlTriggerPx");
    }

    /** Потолок длины клиентского идентификатора — ограничение площадки, и мерит его генератор. */
    @Test
    @DisplayName("U23.13 — длинный клиентский идентификатор отказа здесь не вызывает")
    void u23_13_aLongClientIdIsNotRefusedHere() {
        Order order = order();
        order.setInternalId("vtb-0123456789012345678901234567890123456789");

        PlaceOrderOkxRequest request = mapper.domainToPlaceRequest(order, OkxFixture.INSTRUMENT);

        assertThat(request.getClOrdId()).hasSizeGreaterThan(32)
                .isEqualTo("vtb-0123456789012345678901234567890123456789");
    }

    /**
     * Охрана конъюнктивна (звено `Z1`), а выражение типа исполнения стои́т вне неё.
     * Кейс охраны второго рубежа: шлюз зовёт переход добытой моделью.
     */
    @Test
    @DisplayName("U23.14 — пустая заявка при непустом имени инструмента роняет вычисление типа")
    void u23_14_anEmptyOrderMeetsAnExpressionOutsideTheGuard() {
        assertThatThrownBy(() -> mapper.domainToPlaceRequest(null, OkxFixture.INSTRUMENT))
                .isInstanceOf(NullPointerException.class);

        assertThat(mapper.domainToPlaceRequest((Order) null, null)).isNull();
    }
}
