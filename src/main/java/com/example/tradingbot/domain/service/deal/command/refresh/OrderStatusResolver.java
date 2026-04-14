package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusResolver {

    public Order.Status resolveStatus(OrderExternalSnapshot snapshot) {
        if (snapshot == null) {
            return Order.Status.PENDING;
        }
        return resolveStatus(snapshot.getExternalStatus());
    }

    private Order.Status resolveStatus(String externalStatus) {
        if (externalStatus == null || externalStatus.isBlank()) {
            return Order.Status.PENDING;
        }

        String normalized = externalStatus.trim().toLowerCase();
        return switch (normalized) {
            case "live" -> Order.Status.PENDING;
            case "partially_filled" -> Order.Status.PARTIALLY_COMPLETED;
            case "filled" -> Order.Status.COMPLETED;
            case "canceled", "mmp_canceled" -> Order.Status.CLOSED;
            case "failed", "order_failed", "rejected" -> Order.Status.FAILED;
            default -> Order.Status.PENDING;
        };
    }
}
