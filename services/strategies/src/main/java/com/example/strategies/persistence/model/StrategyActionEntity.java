package com.example.strategies.persistence.model;

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
 * Базовая строка действия шага (таблица strategy_actions, наследование
 * JOINED, дискриминатор {@code action_kind}).
 *
 * <p>Ссылка на деталь денормализована ради ключа «деталь, ключ действия»:
 * дерево неизменяемо, поэтому расхождения денормализации не бывает.
 *
 * <p><b>Уровня сетки у действия нет</b> — уровень свойство транша, сетку
 * задаёт объявление.
 *
 * <p>Само-ссылка действия — двумя колонками: логический ключ цели (форма
 * ввода и чтения) и отложенный FK, который резолвится при сохранении
 * дерева. Логический ключ несущий: порядок вставки строк действий
 * заранее неизвестен, а ключ известен автору.
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

    @Column(name = "target_action_key")
    private String targetActionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_action_id")
    private StrategyActionEntity targetAction;
}
