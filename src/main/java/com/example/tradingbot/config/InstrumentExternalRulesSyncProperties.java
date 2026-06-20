package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация синхронизации внешних правил инструмента
 * (docs/components/InstrumentExternalRulesSyncJob.md): выключатель и
 * период тика. Правила меняются редко — тик не частый. Регистрируется
 * через @ConfigurationPropertiesScan.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "instrument-external-rules-sync")
public class InstrumentExternalRulesSyncProperties {

    /** Выключатель джобы: при false запланированный/ручной тик ничего не делает. */
    private Boolean enabled = true;

    /** CRON-период тика InstrumentExternalRulesSyncJob. */
    private String cron;
}
