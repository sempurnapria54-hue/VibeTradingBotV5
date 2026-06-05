package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
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
import jakarta.persistence.Table;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@link StrategyStep} (таблица strategy_steps).
 * Map stepsByStatus детали хранится плоскими строками: deal_status —
 * ключ map, step_index — порядок в списке; в домене map пересобирает
 * маппер. Условие шага и политика устаревания — JSONB на этой строке.
 * Действия — дочерние строки strategy_actions (cascade ALL).
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_steps")
public class StrategyStepEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_detail_id", nullable = false, updatable = false)
    private StrategyDetailEntity detail;

    @Column(name = "deal_status", nullable = false)
    private String dealStatus;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    @Column(name = "step_type", nullable = false)
    private String stepType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition", nullable = false)
    private String condition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "market_data_expired_setting", nullable = false)
    private String marketDataExpiredSetting;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StrategyActionEntity> actions;
}
