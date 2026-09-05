package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link Candle} (гипертаблица candles). Свеча не
 * входит в JPA-агрегат группы — связь через плоский ключ
 * {@code candle_group_id}. Признак закрытия не хранится: в ряд попадают
 * только закрытые свечи (правило производящей стороны, CandleJob).
 *
 * <p>Ключ составной ({@link CandleId}) — довод в нём же.
 */
@Getter
@Setter
@Entity
@Table(name = "candles")
@IdClass(CandleId.class)
public class CandleEntity extends AuditableEntity {

    @Id
    @Column(name = "candle_group_id", nullable = false, updatable = false)
    private Long candleGroupId;

    @Id
    @Column(name = "open_timestamp", nullable = false, updatable = false)
    private Long openTimestamp;

    @Column(name = "open", nullable = false, precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal close;

    @Column(name = "volume", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal volume;
}
