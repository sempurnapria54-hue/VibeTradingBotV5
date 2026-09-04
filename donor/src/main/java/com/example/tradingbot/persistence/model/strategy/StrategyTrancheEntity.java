package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
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
 * Persistence-проекция {@link StrategyTranche} (таблица
 * strategy_tranches) — каркасный реляционный узел. Шаги транша —
 * дочерние строки strategy_steps (cascade ALL), сгруппированные
 * колонкой tranche_status. Реальная схема (UNIQUE(strategy_detail_id,
 * key), FK) — во Flyway.
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_tranches")
public class StrategyTrancheEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_detail_id", nullable = false, updatable = false)
    private StrategyDetailEntity detail;

    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "level_count", nullable = false)
    private Integer levelCount;

    @Column(name = "level_step",
            precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal levelStep;

    @Column(name = "position_reopen_allowed")
    private Boolean positionReopenAllowed;

    @OneToMany(mappedBy = "tranche", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyStepEntity> steps;
}
