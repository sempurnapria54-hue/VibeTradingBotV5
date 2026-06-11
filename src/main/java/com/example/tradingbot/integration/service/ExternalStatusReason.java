package com.example.tradingbot.integration.service;

/**
 * Код проблемного внешнего статуса (reasonCode у ExternalStatusException).
 * Маппится executor'ом в closeReason сущности. См.
 * docs/rules/controlled-exchange-exceptions.md.
 */
public enum ExternalStatusReason {

    /** Внешний статус неизвестен — не маппится молча в UNKNOWN. */
    UNKNOWN_EXTERNAL_STATUS,

    /** Постановка algo-order на бирже не удалась. */
    ORDER_FAILED,

    /** Частичный отказ algo-order. */
    PARTIALLY_FAILED
}
