package com.example.connector.okx.resolve;

import com.example.tradingbot.domain.resolve.OrderExternalStatusResolver;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.connector.okx.integration.ExternalStatusException;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import org.springframework.stereotype.Component;

/**
 * OKX-резолвер статуса ordinary order: raw OKX {@code state} → доменный
 * {@link Order.Status}. canceled → CANCELED без closeReason (context-
 * dependent, заполняет handler); неизвестный state → ExternalStatusException.
 * См. docs/models/mapping/Order.md (§OKX status resolver).
 */
@Component
public class OkxOrderExternalStatusResolver implements OrderExternalStatusResolver {

    private static final String LIVE = "live";
    private static final String PARTIALLY_FILLED = "partially_filled";
    private static final String FILLED = "filled";
    private static final String CANCELED = "canceled";
    private static final String MMP_CANCELED = "mmp_canceled";

    @Override
    public StatusResolveResult<Order.Status, Order.CloseReason> resolve(String externalStatus) {
        return switch (externalStatus) {
            case LIVE -> StatusResolveResult.of(Order.Status.ACTIVE, null);
            case PARTIALLY_FILLED -> StatusResolveResult.of(Order.Status.PARTIALLY_COMPLETED, null);
            case FILLED -> StatusResolveResult.of(Order.Status.COMPLETED, Order.CloseReason.FILLED);
            case CANCELED, MMP_CANCELED -> StatusResolveResult.of(Order.Status.CANCELED, null);
            default -> throw new ExternalStatusException(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS, externalStatus);
        };
    }
}
