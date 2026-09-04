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
 * Собственных колонок у вида нет: область выхода читается уровнем
 * объявления шага, а доли у выхода не бывает. Таблица заведена ради
 * дискриминатора — без своей строки вид не отличался бы от базового.
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_position_actions")
@DiscriminatorValue("POSITION")
@PrimaryKeyJoinColumn(name = "id")
public class StrategyPositionActionEntity extends StrategyActionEntity {
}
