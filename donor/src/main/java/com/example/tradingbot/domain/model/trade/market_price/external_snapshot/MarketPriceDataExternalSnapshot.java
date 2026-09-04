package com.example.tradingbot.domain.model.trade.market_price.external_snapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот рыночной цены — выход маппера из
 * client-модели OKX ticker до сборки {@code MarketPriceData}. Несёт
 * нормализованные цены и время (без instrumentId — внутренний ID
 * добавляется при сборке MarketPriceData). Сырой OKX DTO за
 * IntegrationService/adapter не выходит. См.
 * docs/components/models/MarketPriceData.md,
 * docs/rules/raw-exchange-dto-boundary.md.
 */
@Value
@Builder
public class MarketPriceDataExternalSnapshot {

    /** Тип инструмента на бирже (OKX instType). */
    String externalInstrumentType;

    /** ID инструмента на бирже (OKX instId). */
    String externalInstrumentId;

    /** Последняя цена сделки. */
    BigDecimal externalLastPrice;

    /** Лучшая цена продажи. */
    BigDecimal externalAskPrice;

    /** Лучшая цена покупки. */
    BigDecimal externalBidPrice;

    /** Время тикера на бирже. */
    OffsetDateTime externalTimestamp;
}
