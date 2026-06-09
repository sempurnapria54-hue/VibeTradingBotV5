package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация jobs расчёта производных рыночных данных
 * (docs/processes/market-data-calculation.md): по джобе — выключатель и
 * CRON; общий потолок размера окна свечей (защита от тяжёлых запросов:
 * грузим ограниченное недавнее окно, не всю историю). CRON читается
 * @Scheduled напрямую из плейсхолдера, выключатель — из этого класса.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "market-data")
public class MarketDataJobsProperties {

    /** Потолок числа недавних закрытых свечей, грузимых в расчёт (ограниченное окно). */
    private Integer candleWindowBars = 1500;

    /** Джоба расчёта индикаторов. */
    private JobProperties indicator = new JobProperties();

    /** Джоба расчёта структуры рынка. */
    private JobProperties structure = new JobProperties();

    /** Джоба расчёта фазы рынка. */
    private JobProperties phase = new JobProperties();

    /** Параметры одной джобы: выключатель и CRON. */
    @Getter
    @Setter
    public static class JobProperties {

        /** Выключатель джобы: при false запланированный/ручной тик ничего не делает. */
        private Boolean enabled = true;

        /** CRON-период тика джобы. */
        private String cron;
    }
}
