package com.example.tradingbot.domain.command;

import lombok.Value;

/**
 * Последняя ошибка retryable-операции: исходный код, сообщение и
 * классификация для retryable-политики. Хранится объектом на Retryable
 * (не парой строк lastErrorCode/lastError). См.
 * docs/components/RetryPolicyService.md,
 * docs/rules/runtime-error-classification.md.
 */
@Value
public class RetryError {

    /** Исходный код ошибки (например, код ответа биржи). */
    String code;

    /** Человекочитаемое сообщение об ошибке. */
    String message;

    /** Классификация ошибки для retryable-политики. */
    RuntimeErrorCode type;
}
