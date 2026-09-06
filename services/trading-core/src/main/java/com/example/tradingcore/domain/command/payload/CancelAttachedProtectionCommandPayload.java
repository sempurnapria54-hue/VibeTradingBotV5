package com.example.tradingcore.domain.command.payload;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры снятия ВСТРОЕННОЙ защиты: её локальный идентификатор и
 * причина из ЕЁ перечня.
 *
 * <p>Словари причин у двух целей не пересекаются — {@code
 * SWITCHED_BY_STRATEGY} есть только здесь, {@code REPLACED_BY_STRATEGY} —
 * только у отдельной условной заявки; на этом и стои́т разведение команд
 * (docs/components/models/ServiceCommand.md).
 */
@Value
public class CancelAttachedProtectionCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор снимаемой встроенной защиты. */
    Long attachedAlgoOrderId;

    /** Причина снятия; ставится write-once. */
    AttachedAlgoOrder.CloseReason cancelReason;
}
