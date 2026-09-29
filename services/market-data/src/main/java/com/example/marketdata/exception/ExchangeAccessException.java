package com.example.marketdata.exception;

/**
 * Отказ, при котором продолжать проход бессмысленно: площадка отказала в
 * доступе, исчерпан лимит либо служебная идентичность для вызова
 * коннектора не добыта.
 *
 * <p>Продолжать обход под исчерпанным лимитом — способ потерять и
 * следующий проход (docs/processes/snapshot-collection.md §«Отказ на
 * проходе»). Недобытая идентичность одинакова для всех инструментов
 * прохода, и обход под ней не удался бы ни на одном. Поэтому класс
 * отделён от рядового отказа чтения.
 */
public class ExchangeAccessException extends ExchangeReadException {

    public ExchangeAccessException(String message) {
        super(message);
    }

    public ExchangeAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
