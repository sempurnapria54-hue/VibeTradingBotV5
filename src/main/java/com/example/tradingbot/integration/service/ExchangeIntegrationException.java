package com.example.tradingbot.integration.service;

/**
 * Ошибка взаимодействия с биржей на границе IntegrationService/adapter:
 * ошибка API (code != "0"), parse или нарушение структурного
 * инварианта ответа (docs/components/ClientService.md).
 *
 * <p>ВНИМАНИЕ: конвенция обработки ошибок (коды, ControllerAdvice vs
 * per-endpoint, таксономия исключений) в проекте пока TBD
 * (.claude/rules/codestyle.md §«Обработка ошибок — TBD»). Это
 * минимальный placeholder; финальная иерархия исключений — открытый
 * вопрос, упирающийся код эскалирует его.
 */
public class ExchangeIntegrationException extends RuntimeException {

    public ExchangeIntegrationException(String message) {
        super(message);
    }

    public ExchangeIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
