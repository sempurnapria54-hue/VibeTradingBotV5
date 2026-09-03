package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация детектора аномалий (AnomalyJob): включение. CRON читается
 * {@code @Scheduled} напрямую из {@code anomaly-job.cron}. Частота —
 * провизорное число (наблюдения контура).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "anomaly-job")
public class AnomalyJobProperties {

    /** Выключатель джоба: false — ни таймерный, ни ручной тик ничего не делают. */
    private Boolean enabled = true;
}
