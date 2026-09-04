package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@link StrategyMarketStructureSetting} (таблица
 * strategy_market_structure_settings) — strategy-scope-строка,
 * объявляется раз (UNIQUE(strategy_id, key)); цель FK результата расчёта
 * MarketStructure (owner-ключевание, трек D). {@code params} — JSONB на
 * строке (MarketStructureParams не полиморфен — тег не нужен). ER/ATR-входы
 * — «мягкие» ключи на индикаторные настройки стратегии. Не Auditable
 * (доменная настройка — value, не Auditable).
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_market_structure_settings")
public class StrategyMarketStructureSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategyEntity strategy;

    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "efficiency_ratio_key")
    private String efficiencyRatioKey;

    @Column(name = "atr_key")
    private String atrKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false)
    private String params;

    @Column(name = "destiny", nullable = false)
    private String destiny;

    @Column(name = "expiration_duration", nullable = false)
    private String expirationDuration;
}
