package com.example.tradingcore.domain.command.payload;

import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры отправки обычной заявки: только локальный идентификатор.
 * Клиентский идентификатор, биржевой и параметры исполнитель берёт из
 * загруженной сущности (docs/components/SubmitOrderExecutor.md).
 */
@Value
public class SubmitOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор отправляемой заявки. */
    Long orderId;
}
