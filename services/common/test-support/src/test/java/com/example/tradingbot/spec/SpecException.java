package com.example.tradingbot.spec;

/** Отказ разбора или вычисления исполнимой спецификации. */
public class SpecException extends RuntimeException {

    public SpecException(String message) {
        super(message);
    }
}
