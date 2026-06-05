package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link StrategyPositionAction} (таблица
 * strategy_position_actions, вид POSITION наследования JOINED).
 * Собственных полей не несёт — вырожденная подтаблица, допустимая при
 * JOINED (docs/decisions/strategy-tree-persistence.md §Действия).
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_position_actions")
@DiscriminatorValue("POSITION")
@PrimaryKeyJoinColumn(name = "id")
public class StrategyPositionActionEntity extends StrategyActionEntity {
}
