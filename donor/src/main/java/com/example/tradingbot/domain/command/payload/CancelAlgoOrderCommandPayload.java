package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import lombok.Value;

/**
 * Параметры CANCEL_ALGO_ORDER: локальный algoOrderId и причина отмены.
 * Cancel-endpoint ветвится по семье algo (из conditionType загруженной
 * сущности). ВСТРОЕННУЮ защиту эта команда не адресует — у неё своя
 * команда и свой словарь причин
 * (docs/components/CancelAttachedProtectionExecutor.md). См.
 * docs/components/CancelAlgoOrderExecutor.md.
 */
@Value
public class CancelAlgoOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный id отменяемого algo-order. */
    Long algoOrderId;

    /** Причина отмены (CANCELED_BY_STRATEGY / REPLACED_BY_STRATEGY / KILL_SWITCH). */
    AlgoOrder.CloseReason cancelReason;
}
