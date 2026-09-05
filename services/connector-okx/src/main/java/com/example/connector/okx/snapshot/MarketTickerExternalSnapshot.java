package com.example.connector.okx.snapshot;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Граничный снапшот тикера: последняя цена, объём и время у площадки.
 *
 * <p><b>Марк-цены и индекса здесь нет</b> — тикер площадки их не отдаёт
 * ({@code docs/components/models/MarketPriceData.md}); они приезжают
 * отдельными агрегатными чтениями, и собирает срез потребитель.
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketTickerExternalSnapshot {

    /** Идентификатор инструмента на площадке. */
    private String externalInstrumentId;

    /** Время тикера у площадки, миллисекунды эпохи. */
    private Long externalTimestamp;

    /** Последняя цена сделки. */
    private BigDecimal lastPrice;

    /** Объём за сутки в базовой валюте. */
    private BigDecimal volume;
}
