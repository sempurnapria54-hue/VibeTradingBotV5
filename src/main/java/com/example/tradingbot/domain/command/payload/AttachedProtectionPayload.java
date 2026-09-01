package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
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

    /**
     * Ценовая база триггера, объявленная стратегией. Доезжает до биржи
     * всегда: биржевой умолчательный тип не используется, а запас до
     * ликвидации считается от марк-цены
     * (docs/models/mapping/Order.md §«Domain Order → OKX request»).
     */
    AlgoOrder.TriggerPriceType triggerPriceType;

    /** Размер (контракты). */
    BigDecimal size;
}
