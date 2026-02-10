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
    @UniqueConstraint(name = "uk_candle_instr_tf_ts", columnNames = {"instrument_id", "timeframe", "timestamp"})
})
public class CandleEntity extends AuditableEntity {

    public static final int TIMEFRAME_LENGTH = 10;
    public static final int STATUS_LENGTH = 50;
    public static final int PRICE_PRECISION = 50;
    public static final int PRICE_SCALE = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    @Column(name = "timeframe", nullable = false, length = TIMEFRAME_LENGTH)
    private String timeframe;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "open", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal close;

    @Column(name = "volume", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal volume;

    @Column(name = "status", length = STATUS_LENGTH)
    private String status;
}
