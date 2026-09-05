package com.example.tradingbot.domain.resolve;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;

/**
 * Переводит внешний статус standalone algo-order биржи в доменный
 * {@link AlgoOrder.Status} + candidate closeReason. Неизвестный/
 * проблемный статус → ExternalStatusException. Per-биржа реализация.
 * См. docs/components/AlgoOrderExternalStatusResolver.md.
 */
public interface AlgoOrderExternalStatusResolver {

    StatusResolveResult<AlgoOrder.Status, AlgoOrder.CloseReason> resolve(String externalStatus);
}
