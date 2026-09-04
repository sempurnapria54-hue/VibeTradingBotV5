package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация интеграции с OKX. Приватные креды (apiKey/secret/passphrase)
 * читаются из Vault per-profile (secret/tradingbot/okx[-test]), как
 * datasource и принципал поверхности. Остаточный хардненинг Vault
 * (политики/approle, ротация, unseal) — операции над самим хранилищем, а не
 * над этой конфигурацией. URL выбирается по региону и конфигурируется извне
 * (docs/integrations/okx/contracts/service-urls.md).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okx")
public class OkxProperties {

    /** Базовый REST URL OKX (например, https://www.okx.com). */
    private String baseUrl;

    /** Демо-флаг: "1" → header x-simulated-trading (demo trading); пусто → production. */
    private String simulated;

    /**
     * API-ключ OKX для приватных endpoint'ов. Читается из Vault
     * (secret/tradingbot/okx[-test], ключ OKX_API_KEY) per-profile; в коммит
     * не попадает.
     */
    private String apiKey;

    /** Секрет API-ключа OKX (HMAC-подпись). Из Vault (ключ OKX_SECRET_KEY), не коммитится. */
    private String secret;

    /** Passphrase API-ключа OKX. Из Vault (ключ OKX_PASSPHRASE), не коммитится. */
    private String passphrase;
}
