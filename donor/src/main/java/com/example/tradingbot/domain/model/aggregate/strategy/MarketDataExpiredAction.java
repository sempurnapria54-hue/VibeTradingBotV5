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
    KILL_SWITCH;

    /**
     * Реакция выводит сделку из штатного ведения управляемым сворачиванием.
     * Ребро и писатель причины — docs/lifecycles/Deal.md §«Причина выхода из
     * штатного ведения».
     */
    public Boolean isGracefulClose() {
        return GRACEFUL_CLOSE.equals(this);
    }

    /**
     * Реакция аварийная: живой риск снимается килл-свичем, а не выводится
     * закрывающими действиями. Форма — жёсткая ступень инструмента
     * (docs/rules/instrument-hold.md).
     */
    public Boolean isKillSwitch() {
        return KILL_SWITCH.equals(this);
    }
}
