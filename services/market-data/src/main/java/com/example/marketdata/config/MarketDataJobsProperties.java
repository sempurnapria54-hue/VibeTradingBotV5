package com.example.marketdata.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация джоб расчёта производных рыночных данных
 * (docs/processes/market-data-calculation.md): по джобе — выключатель;
 * общий потолок окна свечей.
 *
 * <p>Потолок окна — защита от тяжёлого запроса: в расчёт грузится
 * ограниченное недавнее окно, а не вся история
 * (.claude/rules/codestyle.md §«Выборка данных»). CRON читает
 * {@code @Scheduled} прямо из плейсхолдера.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "market-data")
public class MarketDataJobsProperties {

    /** Потолок числа недавних закрытых свечей, грузимых в расчёт. */
    private Integer candleWindowBars = 1500;

    /** Джоба расчёта индикаторов. */
    private JobProperties indicator = new JobProperties();

    /** Джоба расчёта структуры рынка. */
    private JobProperties structure = new JobProperties();

    /** Параметры одной джобы. */
    @Getter
    @Setter
    public static class JobProperties {

        /** Выключатель джобы: при false запланированный и ручной тик ничего не делают. */
        private Boolean enabled = true;
    }
}
