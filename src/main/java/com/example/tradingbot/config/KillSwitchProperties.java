package com.example.tradingbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация аварийного kill-switch (секция kill-switch). Лимит попыток
 * teardown «снять риск → сверить flat рефрешем» до эскалации на биржевой холд
 * (HOLD-Q1); значение — риск-владельца. См. docs/components/KillSwitchExecutor.md.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kill-switch")
public class KillSwitchProperties {

    /** Максимум попыток teardown до эскалации. */
    private Integer maxTeardownAttempts;

    /**
     * Окно выборки сделок радиуса у предусловия снятия жёсткой ступени —
     * СВЕЖИХ, включая терминальные. История сделок инструмента растёт без
     * границы, а остаточный риск живёт на сделках, которых коснулся
     * каскад сворачивания, то есть на свежих
     * (docs/rules/manual-halt.md §«Предусловие «риска не осталось» —
     * машинное, а не заявляемое»).
     */
    private Integer clearanceDealWindow = 200;
}
