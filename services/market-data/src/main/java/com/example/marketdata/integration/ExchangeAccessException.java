package com.example.marketdata.integration;

/**
 * Отказ площадки, при котором продолжать проход бессмысленно: отказ
 * доступа либо исчерпанный лимит.
 *
 * <p>Продолжать обход под исчерпанным лимитом — способ потерять и
 * следующий проход (docs/processes/snapshot-collection.md §«Отказ на
 * проходе»). Поэтому класс отделён от рядового отказа чтения.
 */
public class ExchangeAccessException extends ExchangeReadException {

    public ExchangeAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
