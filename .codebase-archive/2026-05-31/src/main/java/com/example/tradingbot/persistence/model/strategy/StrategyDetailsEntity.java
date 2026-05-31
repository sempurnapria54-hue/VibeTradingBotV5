package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.persistence.model.AuditableEntity;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "strategy_details", uniqueConstraints = {
        @UniqueConstraint(name = "uk_strategy_details_strategy_id_market_phase", columnNames = {
                "strategy_id",
                "market_phase_type"
        })
})
public class StrategyDetailsEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "strategy_id", nullable = false, insertable = false, updatable = false)
    private Long strategyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategyEntity strategy;

    @Column(name = "market_phase_type", nullable = false)
    private String marketPhaseType;

    @Column(name = "phase_entry_policy", nullable = false)
    private String phaseEntryPolicy;

    @Column(name = "risk_per_trade_percent", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal riskPerTradePercent;

    @Column(name = "max_leverage")
    private Integer maxLeverage;

    @Column(name = "target_risk_reward_ratio", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal targetRiskRewardRatio;

    @OrderBy("stepIndex ASC, id ASC")
    @OneToMany(mappedBy = "strategyDetails", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StrategyStepEntity> steps;
}
