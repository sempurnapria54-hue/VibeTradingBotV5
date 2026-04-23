package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class OrderStatusResolver {

    public Order.Status resolveStatus(OrderExternalSnapshot snapshot) {
        if (Objects.isNull(snapshot)) {
            return Order.Status.PENDING;
        }
        return resolveStatus(snapshot.getExternalStatus());
    }

    private Order.Status resolveStatus(String externalStatus) {
        if (StringUtils.isBlank(externalStatus)) {
            return Order.Status.PENDING;
        }

        String normalized = externalStatus.trim()
                                          .toLowerCase(Locale.ROOT);
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
