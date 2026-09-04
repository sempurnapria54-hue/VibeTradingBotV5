package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.order.Order;
import lombok.Value;

/**
 * Параметры CANCEL_ORDER: локальный orderId и причина отмены (пишется
 * в closeReason после подтверждения факта). См.
 * docs/components/CancelOrderExecutor.md.
 */
@Value
public class CancelOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный id отменяемого order. */
    Long orderId;

    /** Причина отмены (CANCELED_BY_STRATEGY / REPLACED_BY_STRATEGY / KILL_SWITCH). */
    Order.CloseReason cancelReason;
}
