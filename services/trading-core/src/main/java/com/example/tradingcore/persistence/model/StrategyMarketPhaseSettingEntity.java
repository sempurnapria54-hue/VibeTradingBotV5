package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Настройка классификации фазы рынка (таблица
 * strategy_market_phase_settings) — на корне, потому что фаза нужна ДО
 * выбора детали.
 *
 * <p>Своих часов и срока свежести у настройки нет: фаза вычисляется на
 * лету и не персистится, поэтому таймфрейма и срока устаревания на строке
 * тоже нет. Клаузы классификации лежат навесом JSONB.
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_market_phase_settings")
public class StrategyMarketPhaseSettingEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategyEntity strategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "phase_rules", nullable = false)
    private String phaseRules;
}
