package com.example.connector.okx.resolve;

import com.example.tradingbot.domain.resolve.AlgoOrderExternalStatusResolver;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.connector.okx.integration.ExternalStatusException;
import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import org.springframework.stereotype.Component;

/**
 * OKX-резолвер статуса standalone algo-order: raw OKX {@code state} →
 * доменный {@link AlgoOrder.Status}. effective → COMPLETED + TRIGGERED;
 * order_failed/partially_failed/unknown → ExternalStatusException. См.
 * docs/models/mapping/AlgoOrder.md (§OKX status resolver).
 */
@Component
public class OkxAlgoOrderExternalStatusResolver implements AlgoOrderExternalStatusResolver {

    private static final String LIVE = "live";
    private static final String PAUSE = "pause";
    private static final String PARTIALLY_EFFECTIVE = "partially_effective";
    private static final String EFFECTIVE = "effective";
    private static final String CANCELED = "canceled";
    private static final String ORDER_FAILED = "order_failed";
    private static final String PARTIALLY_FAILED = "partially_failed";

    @Override
    public StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> resolve(String externalStatus) {
        return switch (externalStatus) {
            case LIVE, PAUSE -> StatusResolveResult.of(AlgoOrder.Status.ACTIVE, null);
            case PARTIALLY_EFFECTIVE -> StatusResolveResult.of(AlgoOrder.Status.PARTIALLY_COMPLETED, null);
            case EFFECTIVE -> StatusResolveResult.of(AlgoOrder.Status.COMPLETED, AlgoOrder.CloseReason.TRIGGERED);
            case CANCELED -> StatusResolveResult.of(AlgoOrder.Status.CANCELED, null);
            case ORDER_FAILED -> throw new ExternalStatusException(ExternalStatusReason.ORDER_FAILED, externalStatus);
            case PARTIALLY_FAILED ->
                    throw new ExternalStatusException(ExternalStatusReason.PARTIALLY_FAILED, externalStatus);
            default -> throw new ExternalStatusException(ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS, externalStatus);
        };
    }
}
