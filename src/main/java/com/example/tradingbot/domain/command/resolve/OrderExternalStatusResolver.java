package com.example.tradingbot.domain.command.resolve;

import com.example.tradingbot.domain.model.core.order.Order;

/**
 * Переводит внешний статус ordinary order биржи в доменный
 * {@link Order.Status} + candidate closeReason. Неизвестный статус не
 * маппится молча — бросает ExternalStatusException. Per-биржа
 * реализация. См. docs/components/OrderExternalStatusResolver.md.
 */
public interface OrderExternalStatusResolver {

    StatusResolveResult<Order.Status, Order.CloseReason> resolve(String externalStatus);
}
