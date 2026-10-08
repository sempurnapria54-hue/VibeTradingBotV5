package com.example.connector.okx.snapshot;

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

    /** Биржевое имя инструмента (OKX instId) — адрес записи в счёт-широком срезе. */
    String externalInstrumentId;

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

    /**
     * Эхо стороны — литералом источника (OKX side: buy/sell); в доменный
     * перечень переводится на переходе снапшота в домен. Операнд сверки, на
     * строку не переносится (docs/models/mapping/AlgoOrder.md §«Сверка эха»).
     */
    String side;

    /**
     * Эхо признака «только уменьшать» (OKX reduceOnly); пусто — источник
     * промолчал. Операнд сверки, на строку не переносится.
     */
    Boolean reduceOnly;

    /** Условие срабатывания (trigger/trailing) как пришло с биржи. */
    ConditionExternalSnapshot condition;

    /** Связанные ordinary order ids (OKX ordId/ordIdList). */
    List<String> linkedOrderExternalIds;

    /** Время создания на бирже (OKX cTime). */
    OffsetDateTime externalCreatedAt;

    /** Время обновления на бирже (OKX uTime; есть в history). */
    OffsetDateTime externalModifiedAt;
}
