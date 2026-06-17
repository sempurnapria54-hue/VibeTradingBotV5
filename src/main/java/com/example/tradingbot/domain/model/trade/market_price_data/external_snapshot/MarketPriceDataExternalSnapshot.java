package com.example.tradingbot.domain.model.trade.market_price_data.external_snapshot;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот рыночной цены (OKX ticker) — проходит
 * границу IntegrationService/adapter как этот объект, не сырым DTO
 * (docs/rules/raw-exchange-dto-boundary.md). Несёт текущие last/ask/bid +
 * время тикера; внутренний instrumentId и доменный MID_PRICE добавляются
 * при сборке MarketPriceData (docs/components/models/MarketPriceData.md,
 * шаг 5). Сырые OKX-строки → BigDecimal (цены) / Long (ts, ms) на маппинге.
 */
@Value
@Builder
public class MarketPriceDataExternalSnapshot {

    /** Тип инструмента на бирже (OKX instType). */
    String externalInstrumentType;

    /** Имя инструмента на бирже (OKX instId). */
    String externalInstrumentId;

    /** Последняя цена сделки (OKX last). */
    BigDecimal externalLastPrice;

    /** Лучшая цена продажи (OKX askPx). */
    BigDecimal externalAskPrice;

    /** Лучшая цена покупки (OKX bidPx). */
    BigDecimal externalBidPrice;

    /** Время тикера, UTC миллисекунды (OKX ts). */
    Long externalTimestamp;
}
