package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Параметры attached protection при создании order со стартовым SL
 * (вложен в CreateOrderCommandPayload.attachedProtection). См.
 * docs/components/CreateOrderExecutor.md.
 */
@Value
@Builder
public class AttachedProtectionPayload {

    /** Внутренний тип attached-защиты. */
    AttachedAlgoOrder.Type attachedType;

    /** Триггерная цена SL. */
    BigDecimal stopLossTriggerPrice;

    /** Размер (контракты). */
    BigDecimal size;
}
