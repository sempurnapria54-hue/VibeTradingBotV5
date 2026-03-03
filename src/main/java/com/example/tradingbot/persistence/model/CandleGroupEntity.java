package com.example.tradingbot.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candle_groups", uniqueConstraints = {
        @UniqueConstraint(name = "uk_candle_group_instrument_timeframe", columnNames = {"instrument_id", "timeframe"})
})
public class CandleGroupEntity extends AuditableEntity {

    public static final int ERROR_MESSAGE_LENGTH = 1024;

    /**
     * Внутренний идентификатор группы свечей.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Инструмент-владелец группы свечей.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, updatable = false)
    private InstrumentEntity instrument;

    /**
     * Таймфрейм группы (например 1m/5m/1H).
     */
    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    /**
     * Текущий статус жизненного цикла загрузки свечей.
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Время открытия первой свечи в UTC миллисекундах.
     */
    @Column(name = "coverage_start_utc_millis")
    private Long coverageStartUtcMillis;

    /**
     * Время закрытия последней свечи в UTC миллисекундах.
     */
    @Column(name = "coverage_end_utc_millis")
    private Long coverageEndUtcMillis;

}
