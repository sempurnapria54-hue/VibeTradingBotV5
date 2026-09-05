package com.example.connector.okx.credentials;

/**
 * Ключи счёта по его идентификатору.
 *
 * <p>Коннектор стейтлесс и обслуживает любой счёт любого тенанта, поэтому
 * ключи не конфигурация процесса, а **операнд вызова**: их адрес
 * выводится из идентификатора счёта, пришедшего в запросе
 * (docs/architecture/platform.md §Безопасность — схема путей;
 * docs/architecture/tenant-and-exchange.md §Ключи).
 *
 * <p><b>Права писать ключи у коннектора нет</b> — пишет их `auth`
 * действием владельца тенанта. Здесь только чтение.
 */
public interface ExchangeCredentialsResolver {

    /**
     * Ключи счёта.
     *
     * @param accountInternalId идентичность биржевого счёта
     * @return ключи и контур, к которому они принадлежат
     * @throws CredentialsUnavailableException ключей по этому счёту нет
     */
    ExchangeCredentials resolve(String accountInternalId);
}
