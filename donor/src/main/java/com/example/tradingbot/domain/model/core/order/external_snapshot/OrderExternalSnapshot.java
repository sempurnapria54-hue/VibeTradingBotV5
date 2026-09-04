package com.example.tradingbot.domain.model.core.order.external_snapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот ordinary order для refresh/search/
 * history flow — единственное, что выходит за adapter. Raw OKX DTO за
 * границу не выходит (docs/rules/raw-exchange-dto-boundary.md). externalStatus
 * — raw, для FSM напрямую не используется (резолвится статус-резолвером).
 * См. docs/models/domain/core/Order.md, docs/models/mapping/Order.md.
 */
@Value
@Builder
public class OrderExternalSnapshot {

    /** Биржевое имя инструмента (OKX instId) — адрес записи в счёт-широком срезе. */
    String externalInstrumentId;

    /** stable client id (OKX clOrdId). */
    String internalId;

    /** Биржевой id (OKX ordId). */
    String externalId;

    /** Тип ордера (источник-нейтральный). */
    String type;

    /** Сторона (BUY/SELL). */
    String side;

    /** Raw статус биржи (OKX state) — diagnostic, не для FSM напрямую. */
    String externalStatus;

    /** Цена (empty→null). */
    BigDecimal price;

    /** Размер (для SWAP/FUTURES — контракты). */
    BigDecimal size;

    /** Исполнено накопленно. */
    BigDecimal accumulatedFillSize;

    /** Средняя цена исполнения. */
    BigDecimal averagePrice;

    /** Комиссия. */
    BigDecimal fee;

    /** Время создания на бирже (OKX cTime). */
    OffsetDateTime externalCreatedAt;

    /** Время обновления на бирже (OKX uTime). */
    OffsetDateTime externalModifiedAt;

    /** Top-level attached client id (OKX attachAlgoClOrdId). */
    String attachedAlgoInternalId;

    /** Top-level TP trigger (OKX tpTriggerPx) — для entry-with-attached-SL. */
    BigDecimal takeProfitTriggerPrice;

    /** Top-level SL trigger (OKX slTriggerPx). */
    BigDecimal stopLossTriggerPrice;

    /** Embedded attached protection (OKX attachAlgoOrds[*]). */
    List<AttachedAlgoOrderExternalSnapshot> attachedAlgoOrders;
}
