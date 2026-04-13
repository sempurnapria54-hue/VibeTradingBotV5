package com.example.tradingbot.domain.model.candle;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Candle extends Auditable {

    /**
     * Внутренний идентификатор свечи.
     */
    private Long id;

    /**
     * Идентификатор группы свечей, к которой относится свеча.
     */
    private Long candleGroupId;

    /**
     * Время открытия свечи в UTC миллисекундах.
     */
    private Long openTimestamp;

    /**
     * Цена открытия свечи.
     */
    private BigDecimal open;

    /**
     * Максимальная цена за интервал свечи.
     */
    private BigDecimal high;

    /**
     * Минимальная цена за интервал свечи.
     */
    private BigDecimal low;

    /**
     * Цена закрытия свечи.
     */
    private BigDecimal close;

    /**
     * Объём торгов за интервал свечи.
     */
    private BigDecimal volume;
}
