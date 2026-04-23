package com.example.tradingbot.domain.model.strategy;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Один StrategyStep =
 * одно общее условие применимости
 * и пакет действий, который должен выполниться целиком.
 */
@Getter
@Setter
public class StrategyStep {

    /**
     * Бизнес-смысл шага.
     */
    private StrategyStepType stepType;

    /**
     * Общее условие применимости шага.
     */
    private StrategyCondition condition;

    /**
     * Все действия, которые нужно выполнить,
     * если condition выполнено.
     */
    private List<StrategyAction> actions;
}
