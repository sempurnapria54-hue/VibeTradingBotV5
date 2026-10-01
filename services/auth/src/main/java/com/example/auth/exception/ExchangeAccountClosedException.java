package com.example.auth.exception;

import lombok.Getter;

/**
 * Биржевой счёт отключён и ключей не принимает.
 *
 * <p>Отзыв ключей и есть смысл отключения
 * (docs/models/domain/core/ExchangeAccount.md, перечень {@code Status}):
 * принятые ключи вернули бы отключённому счёту кред, которым он снова мог бы
 * подписать запрос площадке. Отдельный тип, а не общий негодный вход:
 * причина отказа — состояние счёта, а не форма запроса.
 */
@Getter
public class ExchangeAccountClosedException extends RuntimeException {

    private final String accountInternalId;

    public ExchangeAccountClosedException(String accountInternalId) {
        super("Биржевой счёт отключён и ключей не принимает: " + accountInternalId);
        this.accountInternalId = accountInternalId;
    }
}
