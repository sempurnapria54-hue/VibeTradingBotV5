package com.example.auth.exception;

import lombok.Getter;

/**
 * Биржевого счёта с такой идентичностью в реестре нет.
 *
 * <p>Отдельный тип, а не платформенный {@code IllegalArgumentException}:
 * пояснение отказа пишет наша сторона (docs/rules/error-handling-policy.md
 * §«Пояснение отказа пишет наша сторона, а не платформа»), и собрано оно
 * только из того, что прислал сам вызывающий, — идентичности из пути.
 */
@Getter
public class ExchangeAccountNotFoundException extends RuntimeException {

    private final String accountInternalId;

    public ExchangeAccountNotFoundException(String accountInternalId) {
        super("Биржевой счёт не найден: " + accountInternalId);
        this.accountInternalId = accountInternalId;
    }
}
