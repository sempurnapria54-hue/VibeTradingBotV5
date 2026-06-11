package com.example.tradingbot.domain.command;

/**
 * Тип backoff retry-политики команды. См.
 * docs/components/RetryPolicyService.md.
 */
public enum RetryBackoffType {

    /** Фиксированная задержка между повторами. */
    FIXED,

    /** Экспоненциальный рост задержки (initialDelay · 2^(attempt-1), не выше maxDelay). */
    EXPONENTIAL
}
