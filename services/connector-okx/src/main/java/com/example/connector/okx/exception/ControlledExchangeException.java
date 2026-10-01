package com.example.connector.okx.exception;

/**
 * База controlled exchange exceptions: внешний факт получен (или должен
 * был быть найден), но продолжать normal runtime-flow небезопасно.
 * Ловится на refresh/executor boundary → сущность в ERROR (у нарушения
 * инварианта статус сущности не меняется — оно о чтении, а не о
 * сущности), факты — FSM.
 * См. docs/rules/controlled-exchange-exceptions.md.
 */
public class ControlledExchangeException extends RuntimeException {

    public ControlledExchangeException(String message) {
        super(message);
    }

    public ControlledExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
