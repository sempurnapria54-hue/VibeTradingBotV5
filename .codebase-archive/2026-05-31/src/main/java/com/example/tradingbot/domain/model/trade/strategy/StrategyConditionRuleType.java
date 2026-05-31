package com.example.tradingbot.domain.model.trade.strategy;

/**
 * Тип условия, которое может участвовать в правиле стратегии.
 */
public enum StrategyConditionRuleType {

    /**
     * По инструменту нет открытой позиции.
     */
    NO_OPEN_POSITION,

    /**
     * Входной ордер уже финализирован.
     */
    ENTRY_ORDER_FINALIZED,

    /**
     * Позиция реально открыта.
     */
    POSITION_OPENED,

    /**
     * Attached stop-loss существует.
     */
    ATTACHED_STOP_LOSS_EXISTS,

    /**
     * Основная защита существует.
     */
    MAIN_PROTECTION_EXISTS,

    /**
     * Профит достиг указанного процента.
     */
    PROFIT_PERCENTS_REACHED,

    /**
     * Убыток достиг указанного процента.
     */
    LOSS_PERCENTS_REACHED,

    /**
     * Подтверждён breakout диапазона.
     */
    RANGE_BREAKOUT_CONFIRMED,

    /**
     * Тренд изменился относительно ожидаемого сценария.
     */
    TREND_CHANGED,

    /**
     * Сделка потеряла экономическую эффективность.
     */
    EFFICIENCY_BELOW_THRESHOLD
}
