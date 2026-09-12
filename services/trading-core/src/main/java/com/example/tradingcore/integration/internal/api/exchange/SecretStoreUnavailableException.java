package com.example.tradingcore.integration.internal.api.exchange;

/**
 * Хранилище секретов не ответило коннектору: ключи есть, добыть их сейчас
 * нельзя.
 *
 * <p>Повтор — единственно верная реакция
 * (docs/components/IntegrationService.md §«Классы отказа на границе — дом
 * здесь»); кэш ключей у коннектора делает класс редким: недоступность
 * хранилища останавливает торговлю не мгновенно, а по истечении срока
 * кэша.
 */
public class SecretStoreUnavailableException extends ExchangeIntegrationException {

    public SecretStoreUnavailableException(String message) {
        super(message);
    }
}
