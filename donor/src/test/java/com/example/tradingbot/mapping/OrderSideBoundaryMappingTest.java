package com.example.tradingbot.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.integration.model.okx.request.PlaceOrderOkxRequest;
import com.example.tradingbot.integration.service.ExternalStatusException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Граница словарей стороны заявки: домен знает перечень {@code Order.Side},
 * площадка — литералы {@code buy}/{@code sell}, и перевод живёт ровно на
 * границе (docs/models/mapping/Order.md).
 *
 * <p><b>Зачем этот тест существует.</b> Тропа снапшот → домен была
 * подключена без конвертера, и MapStruct сгенерировал
 * {@code Enum.valueOf(Order.Side.class, "buy")} — то есть каждое обновление
 * заявки с биржи упало бы. Ни один тест этого не показывал: сторону
 * проверяли только в исходящем направлении. Дефект нашёл фокус
 * {@code disaster} на шаге 3 фазы 2, и тест закрывает обе стороны сразу.
 *
 * <p>Состояние здесь настоящее: снапшот с литералом площадки и доменная
 * заявка с перечнем; подменённых предикатов нет
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class OrderSideBoundaryMappingTest {

    private final OrderMapper mapper = buildMapper();

    @Test
    void domainSideLeavesAsExchangeLiteral() {
        Order order = new Order();
        order.setInternalId("ord-1");
        order.setSide(Order.Side.SELL);

        PlaceOrderOkxRequest request = mapper.domainToPlaceRequest(order, "ETH-USDT-SWAP");

        assertThat(request.getSide()).isEqualTo("sell");
    }

    @Test
    void exchangeLiteralArrivesAsDomainSide() {
        OrderExternalSnapshot snapshot = OrderExternalSnapshot.builder().side("buy").build();
        Order order = new Order();

        mapper.updateFromSnapshot(snapshot, order);

        assertThat(order.getSide()).isEqualTo(Order.Side.BUY);
    }

    @Test
    void absentSideLeavesOrderUntouched() {
        OrderExternalSnapshot snapshot = OrderExternalSnapshot.builder().side(null).build();
        Order order = new Order();
        order.setSide(Order.Side.SELL);

        mapper.updateFromSnapshot(snapshot, order);

        assertThat(order.getSide()).isEqualTo(Order.Side.SELL);
    }

    @Test
    void unknownSideRefusesInsteadOfWritingEmpty() {
        OrderExternalSnapshot snapshot = OrderExternalSnapshot.builder().side("hodl").build();
        Order order = new Order();

        assertThatThrownBy(() -> mapper.updateFromSnapshot(snapshot, order))
                .isInstanceOf(ExternalStatusException.class);
        assertThat(order.getSide()).isNull();
    }

    private OrderMapper buildMapper() {
        OrderMapperImpl impl = new OrderMapperImpl();
        ReflectionTestUtils.setField(impl, "okxResponseConverter", new OkxResponseConverter());
        return impl;
    }
}
