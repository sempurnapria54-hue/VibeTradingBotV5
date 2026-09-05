package com.example.connector.okx.snapshot;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот свечи — OKX-массив[9] проходит
 * границу ClientService/adapter как этот объект, не сырым массивом
 * (docs/rules/raw-exchange-dto-boundary.md). Несёт runtime-useful
 * поля; validation-only объёмы (volCcy/volCcyQuote) за границу не
 * выходят. confirm используется производящей стороной как фильтр
 * закрытых свечей и в domain Candle не пишется (docs/models/mapping/Candle.md).
 */
@Value
@Builder
public class CandleExternalSnapshot {

    /** Время открытия свечи, UTC миллисекунды (OKX ts). */
    Long openTimestamp;

    /** Цена открытия (OKX o). */
    BigDecimal open;

    /** Максимум за интервал (OKX h). */
    BigDecimal high;

    /** Минимум за интервал (OKX l). */
    BigDecimal low;

    /** Цена закрытия (OKX c). */
    BigDecimal close;

    /** Объём торгов за интервал (OKX vol). */
    BigDecimal volume;

    /** Признак закрытия свечи (OKX confirm=1 → true). Фильтр, в домен не пишется. */
    Boolean confirm;
}
