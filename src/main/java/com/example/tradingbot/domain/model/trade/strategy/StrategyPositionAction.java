package com.example.tradingbot.domain.model.trade.strategy;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Действия над позицией.
 */
@Getter
@Setter
public class StrategyPositionAction implements StrategyAction {

    /**
     * CLOSE_FULL / CLOSE_PARTIAL.
     */
    private StrategyActionType actionType;

    /**
     * Уровень действия.
     */
    private Integer level;

    /**
     * Для частичного закрытия.
     */
    private BigDecimal closeFractionPercents;
}
