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

    /**
     * Потолок выборки строк разбивки движений на одну сделку. Это
     * единственная коллекция контекста, чья мощность не задана
     * конструкцией сделки: у долгой сделки с частым финансированием она
     * растёт со временем жизни. Упёршаяся в потолок выборка — НЕ
     * усечение, а неполнота: расчёт итогового числа запрещён
     * (docs/spec/deal-context-load.json §cashFlowsComplete).
     */
    private Integer cashFlowWindowLimit = 500;
}
