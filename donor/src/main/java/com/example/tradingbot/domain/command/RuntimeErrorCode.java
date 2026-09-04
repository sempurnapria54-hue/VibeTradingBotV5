package com.example.tradingbot.domain.command;

/**
 * Классификация unexpected runtime-ошибок исполнения (не controlled
 * exchange exceptions и не CalculationError/RiskValidationResult): баг
 * приложения, ошибка взаимодействия с биржей, нарушение инварианта.
 * Ловится на границе FSM/executor, задаёт retryable-политику. Сквозной
 * код (команды/калькуляторы/risk), здесь — первый потребитель. См.
 * docs/rules/runtime-error-classification.md.
 */
public enum RuntimeErrorCode {

    /** Баг приложения: NPE, mapper error, unexpected state — non-retryable. */
    INTERNAL_ERROR,

    /** Ошибка взаимодействия с биржей: timeout, connection reset, gateway/API — может быть retryable. */
    EXCHANGE_ERROR,

    /** Нарушение инварианта/валидации, не должное попасть в runtime — non-retryable. */
    VALIDATION_ERROR
}
