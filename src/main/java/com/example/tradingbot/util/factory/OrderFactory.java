package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.persistence.model.deal.order.OrderEntity;
import lombok.experimental.UtilityClass;

import java.util.UUID;

import static com.example.tradingbot.util.Constant.Status.Order.ORDER_STATUS_CREATED;

@UtilityClass
public class OrderFactory {

    public static OrderEntity createOrderEntity(Long instrumentId, Order request) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.set(instrumentId);
        orderEntity.setInternalId(UUID.randomUUID()
                                      .toString());
        orderEntity.setStatus(ORDER_STATUS_CREATED);
        orderEntity.setSide(request.getSide());
        orderEntity.setType(request.getType());
        orderEntity.setSize(request.getSize());
        orderEntity.setPrice(request.getPrice());
        return orderEntity;
    }
}
