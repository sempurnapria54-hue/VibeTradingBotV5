package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import lombok.Value;

/**
 * Параметры CANCEL_ATTACHED_PROTECTION: локальный id встроенной защиты и
 * причина снятия из ЕЁ перечня. Отдельная условная заявка этой командой
 * не адресуется, и наоборот: словари причин у двух целей не пересекаются
 * (SWITCHED_BY_STRATEGY есть только здесь, REPLACED_BY_STRATEGY — только
 * у отдельной заявки). См.
 * docs/components/CancelAttachedProtectionExecutor.md.
 */
@Value
public class CancelAttachedProtectionCommandPayload implements ServiceCommandPayload {

    /** Локальный id снимаемой встроенной защиты. */
    Long attachedAlgoOrderId;

    /** Причина снятия (SWITCHED_BY_STRATEGY / KILL_SWITCH). */
    AttachedAlgoOrder.CloseReason cancelReason;
}
