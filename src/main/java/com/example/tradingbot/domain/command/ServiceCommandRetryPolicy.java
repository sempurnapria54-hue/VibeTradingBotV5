package com.example.tradingbot.domain.command;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * Политика повтора команды: лимит попыток, задержки и тип backoff.
 * commandType — ключ в конфиге (service-command-retry), здесь не
 * хранится. Биндится из application.yml. См.
 * docs/components/RetryPolicyService.md.
 */
@Getter
@Setter
public class ServiceCommandRetryPolicy {

    /** Максимум попыток до перевода в FAILED. */
    private Integer maxAttempts;

    /** Начальная задержка перед первым повтором. */
    private Duration initialDelay;

    /** Верхняя граница задержки (для EXPONENTIAL). */
    private Duration maxDelay;

    /** Тип backoff. */
    private RetryBackoffType backoff;
}
