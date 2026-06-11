package com.example.tradingbot.domain.model.core.algo_order.external_snapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот standalone algo-order для
 * refresh/service flow — единственное, что выходит за adapter. Raw OKX
 * DTO за границу не выходит (docs/rules/raw-exchange-dto-boundary.md).
 * externalStatus — raw. См. docs/models/domain/core/AlgoOrder.md,
 * docs/models/mapping/AlgoOrder.md.
 */
@Value
@Builder
public class AlgoOrderExternalSnapshot {

    /** stable client id (OKX algoClOrdId). */
    String internalId;

    /** Биржевой id (OKX algoId). */
    String externalId;

    /** Raw статус биржи (OKX state) — diagnostic, не для FSM напрямую. */
    String externalStatus;

    /** Код ошибки биржи (OKX failCode). */
    String failCode;

    /** Фактический размер срабатывания (OKX actualSz). */
    BigDecimal externalSize;

    /** Фактическая цена срабатывания (OKX actualPx). */
    BigDecimal externalPrice;

    /** Время срабатывания (OKX triggerTime). */
    Instant externalTriggerTime;

    /** Условие срабатывания (trigger/trailing) как пришло с биржи. */
    ConditionExternalSnapshot condition;

    /** Связанные ordinary order ids (OKX ordId/ordIdList). */
    List<String> linkedOrderExternalIds;

    /** Время создания на бирже (OKX cTime). */
    OffsetDateTime externalCreatedAt;

    /** Время обновления на бирже (OKX uTime; есть в history). */
    OffsetDateTime externalModifiedAt;
}
