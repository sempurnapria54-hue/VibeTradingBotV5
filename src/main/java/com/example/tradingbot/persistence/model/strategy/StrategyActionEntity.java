package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовая persistence-проекция {@link StrategyAction} (таблица
 * strategy_actions, наследование JOINED, дискриминатор action_kind).
 * strategy_detail_id — денормализованный FK ради
 * DB-UNIQUE(strategy_detail_id, key); безопасен при immutable-дереве.
 * Self-ссылка действия — двумя колонками: target_action_key
 * (логический ключ — форма ввода/чтения) и target_action_id (self-FK,
 * deferrable; CHECK target_action_id <> id), резолвится маппером при
 * сохранении. См. docs/decisions/strategy-tree-persistence.md
 * (§Действия).
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_actions")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "action_kind")
public abstract class StrategyActionEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_step_id", nullable = false, updatable = false)
    private StrategyStepEntity step;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_detail_id", nullable = false, updatable = false)
    private StrategyDetailEntity detail;

    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "level")
    private Integer level;

    @Column(name = "target_action_key")
    private String targetActionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_action_id")
    private StrategyActionEntity targetAction;
}
