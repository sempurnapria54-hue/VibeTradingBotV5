package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.persistence.model.AuditableEntity;
import com.example.tradingbot.util.Constants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция MarketStructure (таблица market_structures).
 * Уровни — отдельные строки market_price_levels (контейнмент cascade).
 * Событие пробоя — плоские breakout_*-колонки (null — пробоя нет).
 * Ключуется настройкой-владельцем (strategy_market_structure_setting_id);
 * ряд строк (строка на окно), не upsert. Идемпотентность —
 * UNIQUE(instrument_id, strategy_market_structure_setting_id, window_end_at)
 * во Flyway (owner-ключевание, трек D). См.
 * docs/models/domain/other/MarketStructure.md.
 */
@Getter
@Setter
@Entity
@Table(name = "market_structures")
public class MarketStructureEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    @Column(name = "strategy_market_structure_setting_id", nullable = false, updatable = false)
    private Long strategyMarketStructureSettingId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "window_start_at", nullable = false)
    private OffsetDateTime windowStartAt;

    @Column(name = "window_end_at", nullable = false)
    private OffsetDateTime windowEndAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "breakout_broken_level_type")
    private String breakoutBrokenLevelType;

    @Column(name = "breakout_direction")
    private String breakoutDirection;

    @Column(name = "breakout_level_price", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal breakoutLevelPrice;

    @Column(name = "breakout_confirmed_at")
    private OffsetDateTime breakoutConfirmedAt;

    @OneToMany(mappedBy = "structure", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MarketPriceLevelEntity> levels;
}
