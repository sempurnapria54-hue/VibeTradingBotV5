package com.example.connector.okx.integration;

/**
 * Ошибка взаимодействия с биржей на границе IntegrationService/adapter:
 * ошибка API (code != "0"), parse или нарушение структурного инварианта
 * ответа. Транспорт/API-сбой биржи — ретраится (EXCHANGE_ERROR);
 * контролируемые внешние факты биржи — отдельная иерархия
 * {@link ControlledExchangeException}. Error-политика внешней поверхности
 * и внутренней градации — docs/rules/error-handling-policy.md.
 */
public class ExchangeIntegrationException extends RuntimeException {

    public ExchangeIntegrationException(String message) {
        super(message);
    }

    public ExchangeIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
