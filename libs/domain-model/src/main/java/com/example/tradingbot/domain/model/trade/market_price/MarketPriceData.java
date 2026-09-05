package com.example.tradingbot.domain.model.trade.market_price;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.util.DomainMath;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Runtime-данные текущих цен инструмента (last / bid / ask + время
 * тикера). RVO, не persisted: историю тикеров не ведём, кэш на первом
 * этапе не используем. Нужен для входа, проверки условий и калькуляторов;
 * собирается в CalculationContext прямо перед расчётом (через
 * MarketPriceDataService по REST ticker). {@code MID_PRICE} не хранится —
 * вычисляется. См. docs/components/models/MarketPriceData.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketPriceData {

    /** Внутренний ID инструмента. */
    private Long instrumentId;

    /** Тип инструмента на бирже (OKX instType). */
    private String externalInstrumentType;

    /** ID инструмента на бирже (OKX instId). */
    private String externalInstrumentId;

    /** Последняя цена сделки. */
    private BigDecimal externalLastPrice;

    /** Лучшая цена продажи. */
    private BigDecimal externalAskPrice;

    /** Лучшая цена покупки. */
    private BigDecimal externalBidPrice;

    /** Время тикера на бирже. */
    private OffsetDateTime externalTimestamp;

    /** Средняя цена bid/ask; {@code null}, если одной из сторон нет. */
    public BigDecimal midPrice() {
        if (isNull(externalBidPrice) || isNull(externalAskPrice)) {
            return null;
        }
        return externalBidPrice.add(externalAskPrice)
                .divide(BigDecimal.TWO, DomainMath.CONTEXT);
    }
}
