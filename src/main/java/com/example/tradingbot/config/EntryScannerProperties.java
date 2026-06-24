package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация джоба поиска входов (EntryScannerJob): включение. CRON
 * читается {@code @Scheduled} напрямую из {@code entry-scanner.cron}.
 * Частота — провизорное число (бэктест/наблюдения).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "entry-scanner")
public class EntryScannerProperties {

    /** Выключатель джоба: false — ни таймерный, ни ручной тик ничего не делают. */
    private Boolean enabled = true;
}
