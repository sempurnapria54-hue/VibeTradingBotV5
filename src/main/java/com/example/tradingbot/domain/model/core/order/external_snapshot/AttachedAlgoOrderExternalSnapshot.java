package com.example.tradingbot.domain.model.core.order.external_snapshot;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный снапшот одного элемента attached protection
 * (OKX attachAlgoOrds[*]). Матчинг с AttachedAlgoOrder — по internalId
 * (client id вложенного TP/SL). Заполненные failCode/failReason →
 * attached ERROR. См. docs/models/mapping/Order.md (§AttachedAlgoOrder).
 */
@Value
@Builder
public class AttachedAlgoOrderExternalSnapshot {

    /** attached algo id из embedded block (OKX attachAlgoId). */
    String externalAttachedId;

    /** client id — основной ключ матчинга (OKX attachAlgoClOrdId). */
    String internalId;

    /** algo id после trigger/создания (OKX algoId). */
    String externalId;

    /** Биржевой тип attached protection (OKX tpOrdKind / future). */
    String externalType;

    /** Размер (OKX sz). */
    BigDecimal size;

    /** Trigger SL (OKX slTriggerPx). */
    BigDecimal stopLossTriggerPrice;

    /** Код ошибки биржи (OKX failCode) — если заполнен, attached ERROR. */
    String failCode;

    /** Диагностика ошибки (OKX failReason). */
    String failReason;
}
