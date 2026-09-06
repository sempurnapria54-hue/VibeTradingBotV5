package com.example.tradingcore.domain.command;

/** Вид отката задержки повтора (docs/components/RetryPolicyService.md). */
public enum RetryBackoffType {

    /** Фиксированная задержка между повторами. */
    FIXED,

    /** Экспоненциальный рост: начальная задержка · 2^(попытка−1), не выше максимальной. */
    EXPONENTIAL
}
