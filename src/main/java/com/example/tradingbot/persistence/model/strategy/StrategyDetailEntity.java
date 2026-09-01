package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.persistence.model.AuditableEntity;
import com.example.tradingbot.util.Constants;
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
 * Persistence-проекция {@link StrategyDetail} (таблица
 * strategy_details) — каркасный реляционный узел. Шаги — дочерние строки
 * strategy_steps (cascade ALL); индикаторы/структуры детали объявлены на
 * стратегии (strategy-scope, трек D) и адресуются по key, своих
 * JSONB-настроек деталь не несёт. Реальная схема (UNIQUE(strategy_id,
 * market_phase_type), FK) — во Flyway. Enum'ы хранятся строкой.
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

    @Column(name = "risk_per_trade_percent",
            precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal riskPerTradePercent;

    @Column(name = "target_risk_reward_ratio",
            precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal targetRiskRewardRatio;

    @Column(name = "position_reopen_allowed")
    private Boolean positionReopenAllowed;

    @OneToMany(mappedBy = "detail", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyStepEntity> steps;
}
