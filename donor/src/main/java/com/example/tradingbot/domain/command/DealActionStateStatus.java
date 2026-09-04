package com.example.tradingbot.domain.command;

/**
 * Статус исполнения одного StrategyAction в рамках Deal. Значения и
 * переходы — docs/lifecycles/DealActionState.md.
 */
public enum DealActionStateStatus {

    /** Action выбран, команды ещё не было (target == null). */
    PLANNED,

    /** CREATE_* создал локальную сущность; target заполнен. */
    CREATED,

    /** SUBMIT_* / CANCEL_* / CLOSE_POSITION отправлен на биржу; факт ещё не подтверждён (ACK не truth). */
    SUBMITTED,

    /** Факт исполнения подтверждён REFRESH_*-контуром. */
    COMPLETED,

    /** Executor упал на retryable-ошибке; ждёт повтора по nextRetryAt. */
    RETRY_PENDING,

    /** Retry исчерпан либо ошибка non-retryable (INTERNAL_ERROR/VALIDATION_ERROR). */
    FAILED,

    /** Action стал неактуален и не исполняется (условие истекло, состояние сделки изменилось). */
    SKIPPED
}
