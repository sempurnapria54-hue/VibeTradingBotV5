package com.example.tradingbot.domain.model.trade.candle;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Одна свеча рынка (OHLCV) в UTC, относящаяся к группе свечей одного
 * инструмента и таймфрейма (CandleGroup). База расчёта индикаторов и
 * анализа рынка. Признак закрытия (confirm) не хранится: в БД попадают
 * только закрытые свечи (правило производящей стороны, CandleJob). См.
 * docs/models/domain/other/Candle.md, docs/models/mapping/Candle.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Candle extends Auditable {

    /** Внутренний идентификатор свечи. */
    private Long id;

    /** ID группы свечей (CandleGroup.id). */
    private Long candleGroupId;

    /** Время открытия свечи, UTC миллисекунды. */
    private Long openTimestamp;

    /** Цена открытия. */
    private BigDecimal open;

    /** Максимум за интервал. */
    private BigDecimal high;

    /** Минимум за интервал. */
    private BigDecimal low;

    /** Цена закрытия. */
    private BigDecimal close;

    /** Объём торгов за интервал. */
    private BigDecimal volume;
}
