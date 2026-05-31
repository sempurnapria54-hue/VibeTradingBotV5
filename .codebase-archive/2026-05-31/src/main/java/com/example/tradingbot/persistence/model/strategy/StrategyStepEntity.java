package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.persistence.converter.StrategyActionListJsonbConverter;
import com.example.tradingbot.persistence.converter.StrategyConditionJsonbConverter;
import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "strategy_steps", uniqueConstraints = {
        @UniqueConstraint(name = "uk_strategy_steps_details_status_step_index", columnNames = {
                "strategy_details_id",
                "deal_status",
                "step_index"
        })
})
public class StrategyStepEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "strategy_details_id", nullable = false, insertable = false, updatable = false)
    private Long strategyDetailsId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_details_id", nullable = false, updatable = false)
    private StrategyDetailsEntity strategyDetails;

    @Column(name = "deal_status", nullable = false)
    private String dealStatus;

    @Column(name = "step_type", nullable = false)
    private String stepType;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    /**
     * JSONB-условие применимости шага стратегии.
     */
    @Convert(converter = StrategyConditionJsonbConverter.class)
    @Column(name = "condition", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private StrategyConditionEntity condition;

    /**
     * JSONB-список actions, которые FSM материализует через ServiceCommand.
     */
    @Convert(converter = StrategyActionListJsonbConverter.class)
    @Column(name = "actions", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private List<StrategyActionEntity> actions;
}
