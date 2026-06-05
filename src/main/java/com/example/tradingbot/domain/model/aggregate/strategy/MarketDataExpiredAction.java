package com.example.tradingbot.domain.model.aggregate.strategy;

/**
 * Реакция шага на устаревание/отсутствие нужных ему рыночных данных.
 * Когда данные устарели — определяет expirationDuration настроек +
 * MarketDataExpirationChecker (docs/rules/market-data-freshness.md);
 * здесь — что делать. См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyMarketDataExpiredSetting).
 */
public enum MarketDataExpiredAction {

    /** Ждать освежения данных, шаг не выполнять. */
    WAIT,

    /** Блокировать шаг (refresh/cancel/close/safety остаются разрешены). */
    BLOCK_STEP,

    /** Управляемое завершение сделки (graceful close). */
    GRACEFUL_CLOSE,

    /** Аварийная остановка (kill switch). */
    KILL_SWITCH
}
