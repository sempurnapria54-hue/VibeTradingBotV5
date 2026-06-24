package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация джоба сопровождения сделок (DealOrchestratorJob): включение
 * и размер окна выборки активных сделок за проход. CRON читается
 * {@code @Scheduled} напрямую из {@code deal-orchestrator.cron}. Частота —
 * провизорное число (бэктест/наблюдения).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "deal-orchestrator")
public class DealOrchestratorProperties {

    /** Выключатель джоба: false — ни таймерный, ни ручной тик ничего не делают. */
    private Boolean enabled = true;

    /** Окно выборки активных сделок за один проход (потенциально большая выборка — ограничена). */
    private Integer batchSize = 100;
}
