package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция MarketPhase (таблица market_phases). Ключуется
 * контейнером-настройкой (strategy_market_phase_setting_id) — осознанное
 * исключение из шаринга по идентичности; хранится история (строка на
 * свечу), актуальная фаза = последняя по candle_timestamp.
 * Идемпотентность — UNIQUE(instrument_id, strategy_market_phase_setting_id,
 * candle_timestamp) во Flyway. См. docs/models/domain/other/MarketPhase.md.
 */
@Getter
@Setter
@Entity
@Table(name = "market_phases")
public class MarketPhaseEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "strategy_market_phase_setting_id", nullable = false, updatable = false)
    private Long strategyMarketPhaseSettingId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "candle_timestamp", nullable = false, updatable = false)
    private OffsetDateTime candleTimestamp;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
