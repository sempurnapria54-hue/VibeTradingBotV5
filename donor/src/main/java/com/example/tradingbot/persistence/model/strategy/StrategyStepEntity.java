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
 *
 * <p><b>Уровень объявления читается по родителю строки, а не по типу
 * шага.</b> Потраншевый шаг ссылается на strategy_tranches и несёт
 * tranche_status ключом группировки; шаг узкой агрегатной поверхности
 * ссылается на strategy_details и несёт deal_status. Ровно одна из двух
 * ссылок непуста — инвариант держит CHECK во Flyway; в домене обе map
 * пересобирает маппер.
 *
 * <p>Условие шага и политика устаревания — JSONB на этой строке;
 * действия — дочерние строки strategy_actions (cascade ALL).
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_steps")
public class StrategyStepEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_tranche_id", updatable = false)
    private StrategyTrancheEntity tranche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_detail_id", updatable = false)
    private StrategyDetailEntity detail;

    @Column(name = "tranche_status")
    private String trancheStatus;

    @Column(name = "deal_status")
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
