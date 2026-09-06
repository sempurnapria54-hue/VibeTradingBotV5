package com.example.tradingcore.domain.command.payload;

import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры снятия обычной заявки: цель и причина.
 *
 * <p>Причина — из перечня <b>обычной заявки</b>; встроенная защита этой
 * командой не адресуется, у неё свой словарь причин и своя команда
 * (docs/components/CancelAlgoOrderExecutor.md §«Встроенной защиты эта
 * команда не адресует»).
 */
@Value
public class CancelOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор снимаемой заявки. */
    Long orderId;

    /** Причина снятия; ставится write-once. */
    Order.CloseReason cancelReason;
}
