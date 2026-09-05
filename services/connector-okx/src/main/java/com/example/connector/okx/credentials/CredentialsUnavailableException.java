package com.example.connector.okx.credentials;

/**
 * Ключей счёта нет в хранилище.
 *
 * <p><b>Отличается от «площадка отвергла креды».</b> Там ключи есть, но
 * площадка их не приняла — это исходящий отказ доступа со своей реакцией
 * (docs/rules/exchange-hold.md). Здесь ключей нет вовсе: счёт
 * зарегистрирован, а ключи не заведены либо отозваны, и повтор этого не
 * лечит. Слитые в один класс, эти два случая дали бы одну реакцию на две
 * разные причины.
 */
public class CredentialsUnavailableException extends RuntimeException {

    public CredentialsUnavailableException(String accountInternalId) {
        super("Ключей счёта нет в хранилище: " + accountInternalId);
    }
}
