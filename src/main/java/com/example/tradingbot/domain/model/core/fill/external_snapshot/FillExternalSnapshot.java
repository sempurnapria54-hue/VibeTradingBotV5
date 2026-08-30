package com.example.tradingbot.domain.model.core.fill.external_snapshot;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный снапшот одного исполнения (fill) — boundary object для
 * REFRESH_FILLS. TradeFill как persisted entity не материализован
 * (OKX-Q1): RefreshFillsExecutor матчит fills с Order/AlgoOrder по
 * order id и агрегирует fillSize/fillPrice/fee в эти сущности. billId —
 * якорь пагинации. См. docs/models/mapping/TradeFill.md (стаб),
 * docs/models/integrations/okx/FillOkxResponse.md.
 */
@Value
@Builder
public class FillExternalSnapshot {

    /** Id сделки на бирже (OKX tradeId). */
    String externalTradeId;

    /** Id ордера, породившего fill (OKX ordId) — матч с Order.externalId. */
    String externalOrderId;

    /** Client order id (OKX clOrdId) — матч с Order.internalId. */
    String internalOrderId;

    /** Якорь пагинации (OKX billId). */
    String externalBillId;

    /** Цена этой сделки (OKX fillPx). */
    BigDecimal fillPrice;

    /** Объём сделки (OKX fillSz; контракты для SWAP/FUTURES). */
    BigDecimal fillSize;

    /** Сторона (buy/sell). */
    String side;

    /** Комиссия (OKX fee; обычно отрицательная — списание). */
    BigDecimal fee;

    /** Валюта комиссии (OKX feeCcy). */
    String feeCurrency;

    /** Время сделки (OKX ts). */
    Instant timestamp;
}
