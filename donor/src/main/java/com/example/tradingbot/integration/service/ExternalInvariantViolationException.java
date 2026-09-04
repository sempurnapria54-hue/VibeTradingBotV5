package com.example.tradingbot.integration.service;

/**
 * Бросает IntegrationService / adapter-layer, если response получен, но
 * нарушает ожидаемый exchange invariant (tdMode != isolated, posSide !=
 * net, side/ordType/reduceOnly != expected). Reaction: сущность → ERROR
 * (closeReason = EXCHANGE_INVARIANT_VIOLATION), Deal → ERROR, Exchange →
 * HOLD. См. docs/rules/controlled-exchange-exceptions.md.
 */
public class ExternalInvariantViolationException extends ControlledExchangeException {

    public ExternalInvariantViolationException(String message) {
        super(message);
    }
}
