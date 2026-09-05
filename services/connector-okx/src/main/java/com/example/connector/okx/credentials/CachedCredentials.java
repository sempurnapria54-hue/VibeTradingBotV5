package com.example.connector.okx.credentials;

import java.time.Instant;
import lombok.Value;

/**
 * Запись кэша ключей: ключи и момент, после которого они перечитываются.
 *
 * <p>Отдельным типом, а не вложенным классом сервиса
 * ({@code .claude/rules/codestyle.md} §«Строгие правила»).
 */
@Value
public class CachedCredentials {

    /** Ключи счёта. */
    ExchangeCredentials credentials;

    /** Момент истечения записи. */
    Instant expiresAt;

    /** Запись ещё действительна. */
    public Boolean isFresh() {
        return Instant.now().isBefore(expiresAt);
    }
}
