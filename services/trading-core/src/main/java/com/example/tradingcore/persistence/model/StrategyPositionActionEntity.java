package com.example.tradingcore.persistence.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Вид действия «позиция» (таблица strategy_position_actions).
 * Собственных полей у него нет: закрытие нетто-экспозиции описывается
 * одним фактом своего вида.
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_position_actions")
@DiscriminatorValue("POSITION")
@PrimaryKeyJoinColumn(name = "id")
public class StrategyPositionActionEntity extends StrategyActionEntity {
}
