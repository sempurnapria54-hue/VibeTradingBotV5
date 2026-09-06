package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.mapping.AlgoOrderMapper;
import com.example.tradingcore.mapping.AlgoOrderMapperImpl;
import com.example.tradingcore.mapping.OrderMapper;
import com.example.tradingcore.mapping.OrderMapperImpl;
import com.example.tradingcore.mapping.RuntimeJsonConverter;
import com.example.tradingcore.persistence.model.AlgoOrderEntity;
import com.example.tradingcore.persistence.model.AttachedAlgoOrderEntity;
import com.example.tradingcore.persistence.model.OrderEntity;
import com.example.tradingcore.persistence.repository.AttachedAlgoOrderRepository;
import com.example.tradingcore.persistence.repository.OrderRepository;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Загрузка ног сделки и навеса условной заявки.
 *
 * <p>Первое условие — <b>пакетность</b>: защиты всех ног читаются одним
 * запросом. Запрос на ногу отработал бы на тесте так же, а на сетке из
 * N траншей дал бы N+1 обращений — дефект, который виден только счётом
 * запросов, а не результатом.
 *
 * <p>Второе — <b>различение «защит нет» и «защиты не грузили»</b>: ноге
 * без защиты ставится пустое поле, а не пустой список.
 *
 * <p>Третье — навес условия едет через конвертер обеими половинами: без
 * подхваченного {@code uses} маппер молча оставил бы колонку пустой.
 */
class OrderGraphLoadTest {

    private static final Long DEAL_ID = 100L;

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AttachedAlgoOrderRepository attachedRepository = mock(AttachedAlgoOrderRepository.class);
    private final OrderMapper orderMapper = new OrderMapperImpl();
    private final OrderDataService dataService =
            new OrderDataService(orderRepository, attachedRepository, orderMapper);
    private final AlgoOrderMapper algoOrderMapper =
            new AlgoOrderMapperImpl(new RuntimeJsonConverter(new ObjectMapper()));

    @Test
    void protectionsOfAllLegsAreReadByOneQuery() {
        when(orderRepository.findByDealId(DEAL_ID)).thenReturn(List.of(orderRow(1L), orderRow(2L)));
        when(attachedRepository.findByOrderIdIn(any())).thenReturn(List.of(attachedRow(11L, 1L)));

        List<Order> orders = dataService.findByDealId(DEAL_ID);

        assertThat(orders).hasSize(2);
        verify(attachedRepository, times(1)).findByOrderIdIn(List.of(1L, 2L));
        verify(attachedRepository, never()).findAllById(any());
    }

    @Test
    void legWithoutProtectionCarriesAnAbsentCollection() {
        when(orderRepository.findByDealId(DEAL_ID)).thenReturn(List.of(orderRow(1L), orderRow(2L)));
        when(attachedRepository.findByOrderIdIn(any())).thenReturn(List.of(attachedRow(11L, 1L)));

        List<Order> orders = dataService.findByDealId(DEAL_ID);

        assertThat(orders.get(0).getAttachedAlgoOrders())
                .extracting(AttachedAlgoOrder::getInternalId).containsExactly("aa-11");
        assertThat(orders.get(1).getAttachedAlgoOrders()).isNull();
    }

    /** Ног у сделки может не быть — вход снят до отправки; запроса защит тогда нет вовсе. */
    @Test
    void dealWithoutLegsAsksNothingAboutProtections() {
        when(orderRepository.findByDealId(DEAL_ID)).thenReturn(List.of());

        assertThat(dataService.findByDealId(DEAL_ID)).isEmpty();

        verify(attachedRepository, never()).findByOrderIdIn(any(Collection.class));
    }

    @Test
    void conditionTreeTravelsThroughTheJsonNavel() {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setDealId(DEAL_ID);
        algoOrder.setInternalId("ao-1");
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setLinkedOrderExternalIds(List.of("ord-1", "ord-2"));
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                new Trigger(new TriggerPrice(AlgoOrder.TriggerPriceType.MARK, new BigDecimal("101.5"), null, null),
                        null),
                null));

        AlgoOrderEntity entity = algoOrderMapper.domainToPersistence(algoOrder);

        assertThat(entity.getCondition()).contains("\"value\":101.5", "\"type\":\"MARK\"");
        assertThat(entity.getLinkedOrderExternalIds()).isEqualTo("[\"ord-1\",\"ord-2\"]");

        AlgoOrder restored = algoOrderMapper.persistenceToDomain(entity);

        assertThat(restored.getCondition().getTrigger().getStopLoss().getValue())
                .isEqualByComparingTo(new BigDecimal("101.5"));
        assertThat(restored.getLinkedOrderExternalIds()).containsExactly("ord-1", "ord-2");
    }

    private OrderEntity orderRow(Long id) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        entity.setDealId(DEAL_ID);
        entity.setInternalId("or-" + id);
        entity.setStatus(Order.Status.ACTIVE.name());
        entity.setType(Order.Type.ENTRY.name());
        return entity;
    }

    private AttachedAlgoOrderEntity attachedRow(Long id, Long orderId) {
        AttachedAlgoOrderEntity entity = new AttachedAlgoOrderEntity();
        entity.setId(id);
        entity.setOrderId(orderId);
        entity.setInternalId("aa-" + id);
        entity.setStatus(AttachedAlgoOrder.Status.ACTIVE.name());
        entity.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS.name());
        return entity;
    }
}
