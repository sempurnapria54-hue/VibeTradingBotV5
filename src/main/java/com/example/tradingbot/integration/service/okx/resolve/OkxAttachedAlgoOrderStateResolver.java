package com.example.tradingbot.integration.service.okx.resolve;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingbot.domain.command.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import org.springframework.stereotype.Component;

/**
 * OKX-резолвер статуса attached protection «по фактам» (у attachAlgoOrds
 * нет полноценного state). failCode/failReason → ERROR; найден без
 * ошибки → ACTIVE; отсутствует → missing-policy по статусу parent Order
 * (матрица — docs/lifecycles/Order.md). См.
 * docs/components/AttachedAlgoOrderStateResolver.md.
 */
@Component
public class OkxAttachedAlgoOrderStateResolver implements AttachedAlgoOrderStateResolver {

    @Override
    public StatusResolveResult<AttachedAlgoOrder.Status, AttachedAlgoOrder.CloseReason> resolve(
            AttachedAlgoOrderExternalSnapshot snapshot, Order.Status parentStatus) {
        if (nonNull(snapshot) && (isNotBlank(snapshot.getFailCode()) || isNotBlank(snapshot.getFailReason()))) {
            return StatusResolveResult.of(AttachedAlgoOrder.Status.ERROR, AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
        }
        if (nonNull(snapshot)) {
            return StatusResolveResult.of(AttachedAlgoOrder.Status.ACTIVE, null);
        }
        return resolveMissing(parentStatus);
    }

    /** Attached отсутствует в snapshot — трактовка зависит от статуса parent Order. */
    private StatusResolveResult<AttachedAlgoOrder.Status, AttachedAlgoOrder.CloseReason> resolveMissing(
            Order.Status parentStatus) {
        return switch (parentStatus) {
            case CREATED, PENDING, ACTIVE, PARTIALLY_COMPLETED ->
                    StatusResolveResult.of(AttachedAlgoOrder.Status.PENDING, null);
            case COMPLETED -> StatusResolveResult.of(AttachedAlgoOrder.Status.ACTIVE, null);
            case CANCELED -> StatusResolveResult.of(AttachedAlgoOrder.Status.CANCELED,
                    AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED);
            case ERROR -> StatusResolveResult.of(AttachedAlgoOrder.Status.ERROR, AttachedAlgoOrder.CloseReason.UNKNOWN);
        };
    }
}
