package com.example.connector.okx.exception;

/**
 * Бросает IntegrationService / adapter-layer, если response получен, но
 * нарушает ожидаемый exchange invariant (tdMode != isolated, posSide !=
 * net, side/ordType != expected) либо несёт значение вне формы контракта
 * (число, время, перечень, длина позиционной строки — сеть разбора
 * OkxExchangeGateway). Reaction: сущность → ERROR
 * (closeReason = EXCHANGE_INVARIANT_VIOLATION), Deal → ERROR, Exchange →
 * HOLD. См. docs/rules/controlled-exchange-exceptions.md.
 */
public class ExternalInvariantViolationException extends ControlledExchangeException {

    public ExternalInvariantViolationException(String message) {
        super(message);
    }

    public ExternalInvariantViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
