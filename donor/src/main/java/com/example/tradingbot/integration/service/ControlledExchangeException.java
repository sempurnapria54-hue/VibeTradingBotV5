package com.example.tradingbot.integration.service;

/**
 * База controlled exchange exceptions: внешний факт получен (или должен
 * был быть найден), но продолжать normal runtime-flow небезопасно.
 * Ловится на refresh/executor boundary → сущность в ERROR, факты — FSM.
 * См. docs/rules/controlled-exchange-exceptions.md.
 */
public class ControlledExchangeException extends RuntimeException {

    public ControlledExchangeException(String message) {
        super(message);
    }
}
