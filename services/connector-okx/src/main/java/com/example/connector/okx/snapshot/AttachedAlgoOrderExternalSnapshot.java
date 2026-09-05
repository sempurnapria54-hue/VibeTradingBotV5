package com.example.connector.okx.snapshot;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
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

    /**
     * Ценовая база триггера — эхо OKX slTriggerPxType, объявленное
     * инвентарём ОБЕИХ форм (элемент attachAlgoOrds родителя и
     * самостоятельная запись). Операнд сверки объявленной базы MARK;
     * пусто — источник промолчал, сверка не запускается.
     */
    AlgoOrder.TriggerPriceType triggerPriceType;

    /**
     * Сырой статус САМОСТОЯТЕЛЬНОЙ записи цикла добычи (OKX state); у
     * элемента attachAlgoOrds родителя пуст. Диагностика: через резолвер
     * внешних статусов не идёт — его операнд entity защиту не принимает.
     */
    String externalStatus;

    /** Код ошибки биржи (OKX failCode) — если заполнен, attached ERROR. */
    String failCode;

    /** Диагностика ошибки (OKX failReason); в колонку не садится. */
    String failReason;
}
