package com.example.tradingcore.exception;

/**
 * Ответ площадки получен, но нарушает инвариант, на котором стои́т наша
 * торговля: режим маржи, сторона позиции, тип и признаки заявки,
 * недостача обязательного поля записи закрытия.
 *
 * <p>Инвариант проверяет ГРАНИЦА — там, где ответ впервые разбирается
 * (docs/components/IntegrationService.md §«Проверка инвариантов
 * контракта»); ядро получает уже класс.
 *
 * <p><b>Статуса сущности класс не меняет и причины закрытия ей не
 * пишет.</b> Нарушение инварианта — факт о ЧТЕНИИ, а не о сущности: её
 * судьба им не наблюдена, и статус остаётся последним применённым фактом.
 * Реакция принадлежит классу отказа — сделка в {@code ERROR}, биржевая
 * ступень 2 (docs/rules/controlled-exchange-exceptions.md).
 */
public class ExternalInvariantViolationException extends ControlledExchangeException {

    public ExternalInvariantViolationException(String message) {
        super(message);
    }
}
