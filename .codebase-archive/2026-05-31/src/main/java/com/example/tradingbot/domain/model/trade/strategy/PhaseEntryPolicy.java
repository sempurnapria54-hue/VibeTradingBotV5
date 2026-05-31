package com.example.tradingbot.domain.model.trade.strategy;

/**
 * Политика входа в сделку для конкретной фазы рынка.
 */
public enum PhaseEntryPolicy {

    /**
     * Торгуем по направлению фазы.
     */
    FOLLOW_PHASE,

    /**
     * Торгуем против доминирующей фазы.
     */
    CONTRARIAN,

    /**
     * Во флэте используем grid-сценарий.
     */
    GRID,

    /**
     * В данной фазе не торгуем.
     */
    NO_TRADE
}
