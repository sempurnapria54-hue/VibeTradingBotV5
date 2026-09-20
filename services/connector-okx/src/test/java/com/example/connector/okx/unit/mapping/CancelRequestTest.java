package com.example.connector.okx.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.connector.okx.integration.external.api.model.okx.request.CancelAlgoOrderOkxRequest;
import com.example.connector.okx.integration.external.api.model.okx.request.CancelOrderOkxRequest;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Запросы снятия: три формы одной операции — группа `U26` документа
 * `.claude/tests/cases/okx-mapping.md`
 * (docs/models/mapping/Order.md §«`Domain Order → request`» и
 * docs/models/mapping/AlgoOrder.md §«OKX request mapping —
 * дополнения»).
 *
 * <p><b>Базовая сборка:</b> три перехода — доменная заявка, доменная
 * условная заявка и встроенная защита; у каждого имя инструмента вторым
 * аргументом.
 *
 * <p><b>Адресов у снятия два, и оба уезжают вместе:</b> площадка
 * принимает и свой идентификатор, и клиентский; предпочтителен первый,
 * но клиентский — то, что у нас есть всегда.
 */
class CancelRequestTest {

    private final OrderMapper orderMapper = Mappers.order();
    private final AlgoOrderMapper algoOrderMapper = Mappers.algoOrder();

    private static Order order(String externalId, String internalId) {
        Order order = new Order();
        order.setExternalId(externalId);
        order.setInternalId(internalId);
        return order;
    }

    private static AlgoOrder algoOrder(String externalId, String internalId) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setExternalId(externalId);
        algoOrder.setInternalId(internalId);
        return algoOrder;
    }

    private static AttachedAlgoOrder attached(String externalId, String internalId) {
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setExternalId(externalId);
        attached.setInternalId(internalId);
        return attached;
    }

    @Test
    @DisplayName("U26.1 — заявка: оба адреса уезжают вместе")
    void u26_1_bothAddressesTravelTogether() {
        CancelOrderOkxRequest request =
                orderMapper.domainToCancelRequest(order("1", "tb-1"), OkxFixture.INSTRUMENT);

        assertThat(request.getInstId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(request.getOrdId()).isEqualTo("1");
        assertThat(request.getClOrdId()).isEqualTo("tb-1");
    }

    @Test
    @DisplayName("U26.2 — заявка без биржевого идентификатора адресуется клиентским")
    void u26_2_theClientIdAddressesTheOrderAlone() {
        CancelOrderOkxRequest request =
                orderMapper.domainToCancelRequest(order(null, "tb-1"), OkxFixture.INSTRUMENT);

        assertThat(request.getOrdId()).isNull();
        assertThat(request.getClOrdId()).isEqualTo("tb-1");
    }

    @Test
    @DisplayName("U26.3 — условная заявка: оба адреса")
    void u26_3_theAlgoOrderCarriesBothAddresses() {
        CancelAlgoOrderOkxRequest request =
                algoOrderMapper.domainToCancelRequest(algoOrder("9", "tb-9"), OkxFixture.INSTRUMENT);

        assertThat(request.getInstId()).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(request.getAlgoId()).isEqualTo("9");
        assertThat(request.getAlgoClOrdId()).isEqualTo("tb-9");
    }

    @Test
    @DisplayName("U26.4 — условная заявка без биржевого идентификатора")
    void u26_4_theAlgoClientIdAddressesAlone() {
        CancelAlgoOrderOkxRequest request =
                algoOrderMapper.domainToCancelRequest(algoOrder(null, "tb-9"), OkxFixture.INSTRUMENT);

        assertThat(request.getAlgoId()).isNull();
        assertThat(request.getAlgoClOrdId()).isEqualTo("tb-9");
    }

    /** Материализованная защита снимается как обычная условная заявка — той же формой запроса. */
    @Test
    @DisplayName("U26.5 — встроенная защита: та же форма запроса, что у условной заявки")
    void u26_5_theProtectionUsesTheSameRequestForm() {
        CancelAlgoOrderOkxRequest request =
                orderMapper.domainToCancelRequest(attached("a9", "tb-p1"), OkxFixture.INSTRUMENT);

        assertThat(request).isInstanceOf(CancelAlgoOrderOkxRequest.class);
        assertThat(request.getAlgoId()).isEqualTo("a9");
        assertThat(request.getAlgoClOrdId()).isEqualTo("tb-p1");
    }

    @Test
    @DisplayName("U26.6 — встроенная защита без биржевого идентификатора")
    void u26_6_theProtectionClientIdAddressesAlone() {
        CancelAlgoOrderOkxRequest request =
                orderMapper.domainToCancelRequest(attached(null, "tb-p1"), OkxFixture.INSTRUMENT);

        assertThat(request.getAlgoId()).isNull();
        assertThat(request.getAlgoClOrdId()).isEqualTo("tb-p1");
    }

    /** Охраны у перехода нет, и это названное свойство: запрос без адреса отвергнет площадка. */
    @Test
    @DisplayName("U26.7 — оба адреса пусты: отказа здесь нет")
    void u26_7_anAddresslessRequestIsStillBuilt() {
        assertThat(orderMapper.domainToCancelRequest(order(null, null), OkxFixture.INSTRUMENT))
                .satisfies(request -> {
                    assertThat(request.getOrdId()).isNull();
                    assertThat(request.getClOrdId()).isNull();
                    assertThat(request.getInstId()).isEqualTo(OkxFixture.INSTRUMENT);
                });
        assertThat(algoOrderMapper.domainToCancelRequest(algoOrder(null, null), OkxFixture.INSTRUMENT))
                .satisfies(request -> {
                    assertThat(request.getAlgoId()).isNull();
                    assertThat(request.getAlgoClOrdId()).isNull();
                });
        assertThat(orderMapper.domainToCancelRequest(attached(null, null), OkxFixture.INSTRUMENT))
                .satisfies(request -> {
                    assertThat(request.getAlgoId()).isNull();
                    assertThat(request.getAlgoClOrdId()).isNull();
                });
    }

    /** Охрана конъюнктивна (звено `Z1`); выражений у переходов снятия нет, поэтому отказа нет. */
    @Test
    @DisplayName("U26.8 — пустая модель при непустом имени: запрос собирается; оба пусты — пустота")
    void u26_8_aConjunctiveGuardOnAllThreeForms() {
        assertThat(orderMapper.domainToCancelRequest((Order) null, OkxFixture.INSTRUMENT))
                .extracting(CancelOrderOkxRequest::getInstId).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(algoOrderMapper.domainToCancelRequest(null, OkxFixture.INSTRUMENT))
                .extracting(CancelAlgoOrderOkxRequest::getInstId).isEqualTo(OkxFixture.INSTRUMENT);
        assertThat(orderMapper.domainToCancelRequest((AttachedAlgoOrder) null, OkxFixture.INSTRUMENT))
                .extracting(CancelAlgoOrderOkxRequest::getInstId).isEqualTo(OkxFixture.INSTRUMENT);

        assertThat(orderMapper.domainToCancelRequest((Order) null, null)).isNull();
        assertThat(algoOrderMapper.domainToCancelRequest(null, null)).isNull();
        assertThat(orderMapper.domainToCancelRequest((AttachedAlgoOrder) null, null)).isNull();
    }

    /** Ветвление пути снятия по семье делает вызывающий, из типа условия. */
    @Test
    @DisplayName("U26.9 — семьи условной заявки в запросе нет")
    void u26_9_theAlgoFamilyIsNotInTheRequest() {
        assertThat(CancelAlgoOrderOkxRequest.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("ordType", "algoFamily", "family");
    }
}
