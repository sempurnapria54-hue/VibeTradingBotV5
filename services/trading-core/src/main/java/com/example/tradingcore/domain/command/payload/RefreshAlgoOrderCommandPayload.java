package com.example.tradingcore.domain.command.payload;

import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры добычи состояния отдельной условной заявки: чью строку
 * обновлять (docs/components/RefreshAlgoOrderExecutor.md).
 */
@Value
public class RefreshAlgoOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор добываемой условной заявки. */
    Long algoOrderId;
}
