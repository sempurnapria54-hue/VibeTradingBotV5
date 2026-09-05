package com.example.marketdata.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@code IndicatorValue} (таблица indicator_values).
 * Наследники по типам индикатора живут в одной таблице (SINGLE_TABLE,
 * дискриминатор indicator_type); ряд строк — строка на свечу, не upsert.
 *
 * <p>Идемпотентность — UNIQUE(instrument_id, indicator_config_id,
 * candle_timestamp) во Flyway: ключ — идентичность вычисления
 * (docs/models/domain/other/IndicatorValue.md).
 *
 * <p>Срока свежести строка не несёт: толерантность приносит читатель
 * (docs/rules/market-data-freshness.md).
 */
@Getter
@Setter
@Entity
@Table(name = "indicator_values")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "indicator_type", discriminatorType = DiscriminatorType.STRING)
public abstract class IndicatorValueEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "indicator_config_id", nullable = false, updatable = false)
    private Long indicatorConfigId;

    @Column(name = "candle_timestamp", nullable = false, updatable = false)
    private OffsetDateTime candleTimestamp;
}
