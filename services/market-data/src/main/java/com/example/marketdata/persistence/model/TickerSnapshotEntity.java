package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@code MarketTicker} (гипертаблица
 * ticker_snapshots) — невосполнимый ряд.
 *
 * <p>Марк-цена и индекс приходят ОТДЕЛЬНЫМИ агрегатными чтениями, поэтому
 * их пустота законна и означает «чтение не дошло». Подстановка последней
 * цены на их место запрещена: она молча меняет ценовой домен
 * (docs/components/models/MarketPriceData.md).
 */
@Getter
@Setter
@Entity
@Table(name = "ticker_snapshots")
@IdClass(MarketSnapshotId.class)
public class TickerSnapshotEntity extends AuditableEntity {

    @Id
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Id
    @Column(name = "external_timestamp", nullable = false, updatable = false)
    private Long externalTimestamp;

    @Column(name = "observed_timestamp", nullable = false)
    private Long observedTimestamp;

    @Column(name = "last_price", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal lastPrice;

    @Column(name = "volume", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal volume;

    @Column(name = "mark_price", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal markPrice;

    @Column(name = "index_price", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal indexPrice;
}
