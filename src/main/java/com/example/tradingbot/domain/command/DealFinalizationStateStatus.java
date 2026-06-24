package com.example.tradingbot.domain.command;

/**
 * Статус исполнения одной финализационной команды сделки. Live (не
 * финальные): PENDING, RETRY_PENDING. Финальные: COMPLETED, FAILED.
 * Переходы — docs/lifecycles/DealFinalizationState.md.
 */
public enum DealFinalizationStateStatus {

    /** Финализация выбрана, команды ещё не было / не подтверждена. */
    PENDING,

    /** Финализация подтверждена (терминальное ребро сделано / факты консолидированы). */
    COMPLETED,

    /** Executor упал на retryable-ошибке; ждёт повтора по nextRetryAt. */
    RETRY_PENDING,

    /** Retry исчерпан либо ошибка non-retryable; сделка идёт ошибочной тропой. */
    FAILED
}
