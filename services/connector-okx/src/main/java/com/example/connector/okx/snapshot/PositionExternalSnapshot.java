package com.example.connector.okx.snapshot;

import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот позиции для обновления Position —
 * единственное, что выходит за adapter. Создаётся только если позиция
 * реально найдена на бирже; не найдена → IntegrationService возвращает
 * null (не пустой snapshot). См. docs/models/domain/core/Position.md,
 * docs/models/mapping/Position.md.
 */
@Value
@Builder
public class PositionExternalSnapshot {

    /** Биржевой id позиции (OKX posId). */
    String externalId;

    /**
     * Биржевое имя инструмента позиции (OKX instId). Нужно там, где
     * снапшоты приходят СРЕЗОМ по множеству инструментов и адресат
     * каждого не задан запросом.
     */
    String externalInstrumentId;

    /** Направление по знаку pos (LONG/SHORT); 0 — не определено. */
    Position.Direction direction;

    /** Размер, нормализованный абсолют (abs(pos)). */
    BigDecimal externalSize;

    /** Средняя цена входа. */
    BigDecimal externalAverageEntryPrice;

    /** Mark price. */
    BigDecimal externalMarkPrice;

    /** Расчётная цена ликвидации. */
    BigDecimal externalLiquidationPrice;

    /** Маржа позиции. */
    BigDecimal externalMargin;

    /** Нереализованный PnL. */
    BigDecimal externalUnrealizedProfit;

    /** Время создания на бирже (OKX cTime). */
    OffsetDateTime externalCreatedAt;

    /** Время обновления на бирже (OKX uTime). */
    OffsetDateTime externalModifiedAt;
}
