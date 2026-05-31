package com.example.tradingbot.persistence.model.candle;

import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.persistence.model.AuditableEntity;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link CandleGroup} (таблица candle_groups).
 * Принадлежит инструменту (owning side агрегата). Реальная схема
 * (уникальность (instrument_id, timeframe), FK) — во Flyway.
 */
@Getter
@Setter
@Entity
@Table(name = "candle_groups")
public class CandleGroupEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false, updatable = false)
    private InstrumentEntity instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeframe", nullable = false)
    private TimeFrame timeframe;

    @Column(name = "external_timeframe", nullable = false)
    private String externalTimeframe;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CandleGroup.Status status;

    @Column(name = "actual_first_utc_millis")
    private Long actualFirstUtcMillis;

    @Column(name = "actual_last_utc_millis")
    private Long actualLastUtcMillis;

    @Column(name = "count", nullable = false)
    private Long count;
}
