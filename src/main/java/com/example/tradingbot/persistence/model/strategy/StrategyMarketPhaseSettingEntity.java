package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.persistence.model.AuditableEntity;
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
 * Persistence-проекция {@link StrategyMarketPhaseSetting} (таблица
 * strategy_market_phase_settings) — каркасный реляционный узел 1:1 к
 * стратегии. Несёт только клаузы классификации (phase_rules JSONB):
 * листовые настройки рыночных данных переехали в собственные strategy-scope-
 * таблицы (трек D), фаза не персистируется (нет timeframe/expiration_duration).
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
