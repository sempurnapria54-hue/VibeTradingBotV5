package com.example.tradingcore.domain.command.payload;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Параметры встроенной защиты, создаваемой ВМЕСТЕ с ногой входа (вложены
 * в {@link CreateOrderCommandPayload}).
 *
 * <p>Защита может быть поставлена на ЧАСТЬ ноги — размер приходит
 * рассчитанным, и покрытие это учитывает
 * (docs/spec/protection-coverage.json).
 *
 * <p>См. docs/components/CreateOrderExecutor.md §«Встроенная защита».
 */
@Value
@Builder
public class AttachedProtectionPayload {

    /** Внутренний тип встроенной защиты. */
    AttachedAlgoOrder.Type attachedType;

    /** Триггерная цена остановки убытка. */
    BigDecimal stopLossTriggerPrice;

    /**
     * Ценовая база триггера, <b>объявленная стратегией</b>. Обязательна:
     * без неё применился бы биржевой умолчательный тип, а запас до
     * ликвидации считается от марк-цены
     * (docs/models/domain/core/Order.md).
     */
    AlgoOrder.TriggerPriceType triggerPriceType;

    /** Размер защиты в контрактах. */
    BigDecimal size;
}
