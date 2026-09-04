package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры SUBMIT_ALGO_ORDER: только локальный algoOrderId. internal/
 * external id, инструмент, параметры executor берёт из загруженной
 * сущности. См. docs/components/SubmitAlgoOrderExecutor.md.
 */
@Value
public class SubmitAlgoOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный id создаваемого algo-order. */
    Long algoOrderId;
}
