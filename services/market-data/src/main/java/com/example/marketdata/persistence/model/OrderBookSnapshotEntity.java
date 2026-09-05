package com.example.marketdata.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@code MarketOrderBook} (гипертаблица
 * order_book_snapshots) — невосполнимый ряд.
 *
 * <p><b>Уровни — JSONB в строке владельца</b>, не отдельной таблицей: FK
 * на них ниоткуда не ведёт, а нормализация дала бы сорок строк на срез
 * вместо одной — на проходе раз в минуту по всему листингу это два
 * порядка объёма ряда, который не чистится
 * (docs/models/domain/other/MarketOrderBook.md §Персистентность).
 */
@Getter
@Setter
@Entity
@Table(name = "order_book_snapshots")
@IdClass(MarketSnapshotId.class)
public class OrderBookSnapshotEntity extends AuditableEntity {

    @Id
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Id
    @Column(name = "external_timestamp", nullable = false, updatable = false)
    private Long externalTimestamp;

    @Column(name = "observed_timestamp", nullable = false)
    private Long observedTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bids", nullable = false)
    private String bids;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asks", nullable = false)
    private String asks;
}
