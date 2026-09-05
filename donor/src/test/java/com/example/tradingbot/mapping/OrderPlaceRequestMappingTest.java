package com.example.tradingbot.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.integration.model.okx.request.AttachAlgoOrdOkxRequest;
import com.example.tradingbot.integration.model.okx.request.PlaceOrderOkxRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Постановка встроенной защиты на биржу (Т5): place-запрос несёт
 * attachAlgoOrds — client id, триггер, ценовую базу (всегда, биржевой
 * default не используется) и market-флаг исполнения; sz элемента не
 * отправляется. У заявки без защиты ключ attachAlgoOrds в JSON
 * отсутствует — и при null, и при пустом списке. См.
 * docs/models/mapping/Order.md (§Domain Order → OKX request).
 */
class OrderPlaceRequestMappingTest {

    private final OrderMapper mapper = buildMapper();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void placeRequestCarriesAttachedProtection() {
        Order order = order();
        order.setAttachedAlgoOrders(List.of(attached()));

        PlaceOrderOkxRequest request = mapper.domainToPlaceRequest(order, "ETH-USDT-SWAP");

        assertThat(request.getAttachAlgoOrds()).hasSize(1);
        AttachAlgoOrdOkxRequest element = request.getAttachAlgoOrds().get(0);
        assertThat(element.getAttachAlgoClOrdId()).isEqualTo("att-1");
        assertThat(element.getSlTriggerPx()).isEqualTo("2000.5");
        assertThat(element.getSlTriggerPxType()).isEqualTo("mark");
        assertThat(element.getSlOrdPx()).isEqualTo("-1");
    }

    @Test
    void attachedElementJsonHasNoSize() throws JsonProcessingException {
        Order order = order();
        order.setAttachedAlgoOrders(List.of(attached()));

        String body = json.writeValueAsString(mapper.domainToPlaceRequest(order, "ETH-USDT-SWAP"));

        assertThat(body).contains("\"attachAlgoOrds\"");
        assertThat(body).contains("\"slTriggerPxType\":\"mark\"");
        assertThat(body).doesNotContain("\"sz\":\"0.02\"");
    }

    @Test
    void orderWithoutProtectionOmitsAttachKey() throws JsonProcessingException {
        Order order = order();
        order.setAttachedAlgoOrders(null);
        assertThat(json.writeValueAsString(mapper.domainToPlaceRequest(order, "ETH-USDT-SWAP")))
                .doesNotContain("attachAlgoOrds");

        order.setAttachedAlgoOrders(List.of());
        assertThat(json.writeValueAsString(mapper.domainToPlaceRequest(order, "ETH-USDT-SWAP")))
                .doesNotContain("attachAlgoOrds");
    }

    private Order order() {
        Order order = new Order();
        order.setInternalId("ord-1");
        order.setSide(Order.Side.BUY);
        order.setSize(new BigDecimal("0.05"));
        return order;
    }

    private AttachedAlgoOrder attached() {
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setInternalId("att-1");
        attached.setStatus(AttachedAlgoOrder.Status.CREATED);
        attached.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
        attached.setStopLossTriggerPrice(new BigDecimal("2000.5"));
        attached.setTriggerPriceType(AlgoOrder.TriggerPriceType.MARK);
        attached.setSize(new BigDecimal("0.02"));
        return attached;
    }

    private OrderMapper buildMapper() {
        OrderMapperImpl impl = new OrderMapperImpl();
        ReflectionTestUtils.setField(impl, "okxResponseConverter", new OkxResponseConverter());
        return impl;
    }
}
