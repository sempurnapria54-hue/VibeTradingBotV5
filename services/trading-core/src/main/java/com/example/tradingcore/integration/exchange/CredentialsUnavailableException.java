package com.example.tradingcore.integration.exchange;

/**
 * Ключей счёта в хранилище нет: коннектор ответил, и ответ говорит, что
 * подписывать нечем.
 *
 * <p><b>Это не «хранилище не ответило».</b> Там ответа нет, и повтор
 * помогает, как только хранилище вернётся; здесь ответ получен, ключей не
 * заводили, и повтор даст тот же исход
 * (docs/components/IntegrationService.md §«Классы отказа на границе — дом
 * здесь»). Слитые в один класс, они дали бы одну реакцию на две
 * противоположные причины.
 */
public class CredentialsUnavailableException extends ExchangeIntegrationException {

    public CredentialsUnavailableException(String message) {
        super(message);
    }
}
