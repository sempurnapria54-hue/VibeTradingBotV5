package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.order.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusResolver {

    public Order.Status resolve(String externalStatus) {
        if (externalStatus == null || externalStatus.isBlank()) {
            return Order.Status.PENDING;
        }

        String normalized = externalStatus.trim().toLowerCase();
        return switch (normalized) {
            case "live" -> Order.Status.PENDING;
            case "partially_filled" -> Order.Status.PARTIALLY_COMPLETED;
            case "filled" -> Order.Status.COMPLETED;
            case "canceled" -> Order.Status.CLOSED;
            case "order_failed" -> Order.Status.FAILED;
            default -> Order.Status.PENDING;
        };
    }
}
