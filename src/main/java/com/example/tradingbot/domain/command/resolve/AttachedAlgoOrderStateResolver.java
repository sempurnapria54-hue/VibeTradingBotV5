package com.example.tradingbot.domain.command.resolve;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;

/**
 * Определяет доменный {@link AttachedAlgoOrder.Status} «по фактам»
 * (snapshot + failCode/failReason + статус parent Order), т.к. у OKX
 * attachAlgoOrds нет полноценного state. Матрица фактов — в
 * docs/lifecycles/Order.md. Per-биржа реализация. См.
 * docs/components/AttachedAlgoOrderStateResolver.md.
 */
public interface AttachedAlgoOrderStateResolver {

    StatusResolveResult<AttachedAlgoOrder.Status, AttachedAlgoOrder.CloseReason> resolve(
            AttachedAlgoOrderExternalSnapshot snapshot, Order.Status parentStatus);
}
