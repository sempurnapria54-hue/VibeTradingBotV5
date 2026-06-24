package com.example.tradingbot.domain.safety;

/**
 * Скоуп реактивного safety-холда: на каком уровне выставляется блокировка
 * торговли и kill-switch. Уровни error-градации
 * (docs/rules/error-handling-policy.md): инструмент = уровень 3
 * (docs/rules/instrument-hold.md), биржа = уровень 4
 * (docs/rules/exchange-hold.md). Общий enum для {@link HoldSignal} (сигнал
 * из прохода) и AnomalyReport (журнал инцидента).
 */
public enum HoldScope {

    /** Холд одного инструмента (уровень 3): локализованная риск-ошибка. */
    INSTRUMENT,

    /** Холд всей биржи (уровень 4): каскад на все инструменты биржи. */
    EXCHANGE
}
