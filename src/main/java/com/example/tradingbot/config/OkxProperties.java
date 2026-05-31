package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация интеграции с OKX. Шаг 1 использует только публичные
 * endpoint'ы — ключи/секреты не нужны (вводятся с приватными
 * операциями и на шаге 9 «Безопасность»). URL выбирается по региону
 * и конфигурируется извне (docs/integrations/okx/contracts/service-urls.md).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "okx")
public class OkxProperties {

    /** Базовый REST URL OKX (например, https://www.okx.com). */
    private String baseUrl;

    /** Демо-флаг: "1" → header x-simulated-trading (demo trading); пусто → production. */
    private String simulated;
}
