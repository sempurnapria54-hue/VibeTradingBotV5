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
}
