package com.example.tradingbot.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candle", uniqueConstraints = {
    @UniqueConstraint(name = "uk_candle_group_ts", columnNames = {"candle_group_id", "timestamp"})
})
public class CandleEntity extends AuditableEntity {

    public static final int PRICE_PRECISION = 50;
    public static final int PRICE_SCALE = 30;

    /** Внутренний идентификатор свечи. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Идентификатор группы свечей, к которой относится свеча. */
    @Column(name = "candle_group_id", nullable = false, updatable = false, insertable = false)
    private Long candleGroupId;

    /** Метка времени свечи в UTC миллисекундах. */
    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    /** Цена открытия свечи. */
    @Column(name = "open", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal open;

    /** Максимальная цена за интервал свечи. */
    @Column(name = "high", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal high;

    /** Минимальная цена за интервал свечи. */
    @Column(name = "low", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal low;

    /** Цена закрытия свечи. */
    @Column(name = "close", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal close;

    /** Объём торгов за интервал свечи. */
    @Column(name = "volume", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal volume;
}
