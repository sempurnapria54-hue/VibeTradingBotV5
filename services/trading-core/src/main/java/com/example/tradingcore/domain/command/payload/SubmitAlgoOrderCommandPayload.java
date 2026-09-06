package com.example.tradingcore.domain.command.payload;

import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры отправки условной заявки: только локальный идентификатор —
 * остальное исполнитель берёт из загруженной сущности
 * (docs/components/SubmitAlgoOrderExecutor.md).
 */
@Value
public class SubmitAlgoOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор отправляемой условной заявки. */
    Long algoOrderId;
}
