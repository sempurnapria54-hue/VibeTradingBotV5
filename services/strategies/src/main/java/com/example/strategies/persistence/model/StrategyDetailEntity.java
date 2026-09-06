package com.example.strategies.persistence.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Набор торговых правил одной фазы рынка (таблица strategy_details).
 *
 * <p>Объявления траншей — дочерние строки, и потраншевые шаги висят на
 * них; собственные строки шагов у детали — только узкая агрегатная
 * поверхность (выход и аварийный выход уровня сделки).
 *
 * <p><b>Риск-поля nullable по существу:</b> у неторгуемой детали риска
 * нет вовсе. Собственных настроек индикаторов и структуры деталь не
 * держит — она адресует каталог стратегии по ключу
 * (docs/models/domain/aggregate/Strategy.md §StrategyDetail).
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_details")
public class StrategyDetailEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategyEntity strategy;

    @Column(name = "market_phase_type", nullable = false)
    private String marketPhaseType;

    @Column(name = "phase_entry_policy", nullable = false)
    private String phaseEntryPolicy;

    @Column(name = "risk_per_action_percent", precision = 36, scale = 18)
    private BigDecimal riskPerActionPercent;

    @Column(name = "cumulative_risk_per_deal_multiplier", precision = 36, scale = 18)
    private BigDecimal cumulativeRiskPerDealMultiplier;

    @Column(name = "strategy_simultaneous_risk_per_deal_percent", precision = 36, scale = 18)
    private BigDecimal strategySimultaneousRiskPerDealPercent;

    @Column(name = "strategy_catastrophic_risk_per_deal_multiplier", precision = 36, scale = 18)
    private BigDecimal strategyCatastrophicRiskPerDealMultiplier;

    @Column(name = "target_risk_reward_ratio", precision = 36, scale = 18)
    private BigDecimal targetRiskRewardRatio;

    @OneToMany(mappedBy = "detail", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyTrancheEntity> tranches;

    /** Строки шагов УЗКОЙ агрегатной поверхности; потраншевые висят на транше. */
    @OneToMany(mappedBy = "detail", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyStepEntity> steps;
}
