package com.example.tradingcore.integration.exchange;

/**
 * Ответ площадки получен, но нарушает инвариант, на котором стои́т наша
 * торговля: режим маржи, сторона позиции, тип и признаки заявки,
 * недостача обязательного поля записи закрытия.
 *
 * <p>Инвариант проверяет ГРАНИЦА — там, где ответ впервые разбирается
 * (docs/components/IntegrationService.md §«Проверка инвариантов
 * контракта»); ядро получает уже класс. Причина закрытия сущности —
 * {@code EXCHANGE_INVARIANT_VIOLATION}
 * (docs/rules/controlled-exchange-exceptions.md).
 */
public class ExternalInvariantViolationException extends ControlledExchangeException {

    public ExternalInvariantViolationException(String message) {
        super(message);
    }
}
