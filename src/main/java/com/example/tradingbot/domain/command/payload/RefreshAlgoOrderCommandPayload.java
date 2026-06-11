package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры REFRESH_ALGO_ORDER: локальный algoOrderId. Evidence-cycle
 * обходит исполнитель внутри команды. См.
 * docs/components/RefreshAlgoOrderExecutor.md.
 */
@Value
public class RefreshAlgoOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный id обновляемого algo-order. */
    Long algoOrderId;
}
