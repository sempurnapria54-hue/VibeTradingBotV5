package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки тика объявления потребности стратегий владельцу рыночных
 * данных.
 *
 * <p>Выключатель и CRON — требование конвенции джоб
 * (.claude/rules/codestyle.md §Джобы): период задаётся конфигурацией, а не
 * хардкодом, и при выключенном флаге не делают ничего ни запланированный
 * тик, ни ручной.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "strategy-demand")
public class StrategyDemandProperties {

    /** Тик включён. */
    private Boolean enabled;

    /** Расписание тика. */
    private String cron;
}
