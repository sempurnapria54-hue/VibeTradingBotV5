package com.example.marketdata.persistence.model;

import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link CandleGroup} (таблица candle_groups) —
 * единица сбора свечей одного инструмента и таймфрейма.
 *
 * <p>Связь с инструментом — плоским FK, без {@code @ManyToOne}: группа
 * читается и пишется отдельно от инструмента, а JPA-агрегата из них
 * market-data не строит.
 *
 * <p><b>Горизонт бэкфилла — колонка группы</b>, а не инструмента: глубину
 * называет требование потребителя, и у 1m и 1D одного инструмента она
 * разная (docs/processes/candle-loading.md §«Кто заводит группу»).
 *
 * <p>Енумы — только в домене; таймфрейм и статус хранятся строкой
 * (значение = {@code name()}).
 */
@Getter
@Setter
@Entity
@Table(name = "candle_groups")
public class CandleGroupEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "timeframe", nullable = false, updatable = false)
    private String timeframe;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "planned_first_utc_millis")
    private Long plannedFirstUtcMillis;

    @Column(name = "actual_first_utc_millis")
    private Long actualFirstUtcMillis;

    @Column(name = "actual_last_utc_millis")
    private Long actualLastUtcMillis;

    @Column(name = "count", nullable = false)
    private Long count;
}
