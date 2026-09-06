package com.example.tradingcore.domain.command;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * Политика повтора команды: предел попыток, задержки и вид отката. Тип
 * команды — ключ в конфигурации, здесь не хранится.
 *
 * <p>Биндится из конфигурации приложения
 * (docs/components/RetryPolicyService.md).
 */
@Getter
@Setter
public class ServiceCommandRetryPolicy {

    /** Предел попыток до перевода строки в отказ. */
    private Integer maxAttempts;

    /** Начальная задержка перед первым повтором. */
    private Duration initialDelay;

    /** Верхняя граница задержки — для экспоненциального отката. */
    private Duration maxDelay;

    /** Вид отката. */
    private RetryBackoffType backoff;
}
