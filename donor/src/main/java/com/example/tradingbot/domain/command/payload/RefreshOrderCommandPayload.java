package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры REFRESH_ORDER: локальный orderId. Evidence-cycle обходит
 * исполнитель внутри команды. См. docs/components/RefreshOrderExecutor.md.
 */
@Value
public class RefreshOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный id обновляемого order. */
    Long orderId;
}
