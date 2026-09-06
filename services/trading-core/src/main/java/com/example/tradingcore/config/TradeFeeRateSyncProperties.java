package com.example.tradingcore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки тика синка ставок комиссии.
 *
 * <p>Выключатель и CRON — требование конвенции джоб
 * (.claude/rules/codestyle.md §Джобы): период тика задаётся
 * конфигурацией, а не хардкодом, и при выключенном флаге не делают
 * ничего ни запланированный тик, ни ручной.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "trade-fee-rate-sync")
public class TradeFeeRateSyncProperties {

    /** Тик включён. */
    private Boolean enabled;

    /** Расписание тика. */
    private String cron;
}
