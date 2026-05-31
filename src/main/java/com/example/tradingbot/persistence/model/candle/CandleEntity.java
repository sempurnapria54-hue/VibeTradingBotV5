package com.example.tradingbot.persistence.model.candle;

import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.persistence.model.AuditableEntity;
import com.example.tradingbot.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link Candle} (таблица candles). Свеча не
 * входит в JPA-агрегат группы — связь через плоский FK candle_group_id.
 * Идемпотентность загрузки — уникальность (candle_group_id,
 * open_timestamp) во Flyway. confirm не хранится (только закрытые свечи).
 */
@Getter
@Setter
@Entity
@Table(name = "candles")
public class CandleEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candle_group_id", nullable = false, updatable = false)
    private Long candleGroupId;

    @Column(name = "open_timestamp", nullable = false)
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
