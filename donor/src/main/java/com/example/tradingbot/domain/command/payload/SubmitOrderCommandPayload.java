package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры SUBMIT_ORDER: только локальный orderId. internalId
 * (clOrdId) / externalId executor берёт из загруженной сущности. См.
 * docs/components/SubmitOrderExecutor.md.
 */
@Value
public class SubmitOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный id создаваемого order. */
    Long orderId;
}
