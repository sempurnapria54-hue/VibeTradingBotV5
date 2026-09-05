package com.example.marketdata.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация синка листинга и справочных правил инструментов
 * (docs/components/InstrumentExternalRulesSyncJob.md): выключатель и
 * потолок числа инструментов, чьи правила обновляются за один тик.
 *
 * <p><b>Правила читаются ПОИНСТРУМЕНТНО</b> — агрегатного чтения правил
 * у площадки нет, — поэтому проход по всему листингу ограничен окном:
 * бюджет лимитов делится со сбором срезов, который важнее (срез не
 * добывается потом, правила добываются).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "instrument-sync")
public class InstrumentSyncProperties {

    /** Выключатель джобы: при false запланированный и ручной тик ничего не делают. */
    private Boolean enabled = true;

    /** Сколько инструментов обновляет правила за один тик. */
    private Integer rulesBatchSize = 50;
}
