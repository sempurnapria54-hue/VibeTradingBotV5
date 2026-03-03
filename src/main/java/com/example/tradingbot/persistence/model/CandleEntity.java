package com.example.tradingbot.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_candle_group_id_open_timestamp", columnNames = {"candle_group_id", "open_timestamp"})
})
public class CandleEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор свечи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Идентификатор группы свечей, к которой относится свеча.
     */
    @Column(name = "candle_group_id", nullable = false, updatable = false, insertable = false)
    private Long candleGroupId;

    /**
     * Время открытия свечи в UTC миллисекундах.
     */
    @Column(name = "open_timestamp", nullable = false)
    private Long openTimestamp;

    /**
     * Цена открытия свечи.
     */
    @Column(name = "open", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal open;

    /**
     * Максимальная цена за интервал свечи.
     */
    @Column(name = "high", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal high;

    /**
     * Минимальная цена за интервал свечи.
     */
    @Column(name = "low", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal low;

    /**
     * Цена закрытия свечи.
     */
    @Column(name = "close", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal close;

    /**
     * Объём торгов за интервал свечи.
     */
    @Column(name = "volume", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal volume;
}
