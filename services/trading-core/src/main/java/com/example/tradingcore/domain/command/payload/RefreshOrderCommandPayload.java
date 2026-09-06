package com.example.tradingcore.domain.command.payload;

import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры добычи состояния обычной заявки: чью строку обновлять.
 *
 * <p>Счёт и инструмент приезжают контекстом прохода; цель названа
 * параметром, потому что заявок у сделки много, а команда адресует одну
 * (docs/components/RefreshOrderExecutor.md).
 */
@Value
public class RefreshOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор добываемой заявки. */
    Long orderId;
}
