package com.example.tradingbot.domain.model.trade.strategy;

/**
 * Действия делаются типизированными.
 * <p>
 * Это лучше, чем один плоский объект
 * с большим количеством nullable-полей.
 */
public interface StrategyAction {

    /**
     * Стабильный id action внутри immutable стратегии.
     */
    Long getId();

    /**
     * Задать стабильный id action при создании стратегии или восстановлении из JSON.
     */
    void setId(Long id);
}
