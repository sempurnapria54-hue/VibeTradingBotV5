package com.example.tradingcore.domain.command.payload;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры снятия отдельной условной заявки.
 *
 * <p>Эндпоинт ветвится по семье условия, и семью исполнитель берёт из
 * загруженной сущности, а не отсюда: параметр, дублирующий её поле, мог бы
 * с ним разойтись (docs/models/mapping/AlgoOrder.md).
 */
@Value
public class CancelAlgoOrderCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор снимаемой условной заявки. */
    Long algoOrderId;

    /** Причина снятия из перечня условной заявки; ставится write-once. */
    AlgoOrder.CloseReason cancelReason;
}
