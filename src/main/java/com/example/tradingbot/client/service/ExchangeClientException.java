package com.example.tradingbot.client.service;

/**
 * Ошибка взаимодействия с биржей на границе ClientService/adapter:
 * ошибка API (code != "0"), parse или нарушение структурного
 * инварианта ответа (docs/components/ClientService.md).
 *
 * <p>ВНИМАНИЕ: конвенция обработки ошибок (коды, ControllerAdvice vs
 * per-endpoint, таксономия исключений) в проекте пока TBD
 * (.claude/rules/codestyle.md §«Обработка ошибок — TBD»). Это
 * минимальный placeholder; финальная иерархия исключений — открытый
 * вопрос, упирающийся код эскалирует его.
 */
public class ExchangeClientException extends RuntimeException {

    public ExchangeClientException(String message) {
        super(message);
    }

    public ExchangeClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
