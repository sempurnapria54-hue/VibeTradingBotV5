package com.example.tradingbot.domain.command;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовое retry-состояние операции, наследуемое persisted-моделями
 * (DealActionState). Несёт счётчик попыток, лимит, момент следующего
 * допустимого повтора и последнюю ошибку. Механика повтора —
 * docs/components/RetryPolicyService.md.
 */
@Getter
@Setter
public abstract class Retryable {

    /** Сколько раз операция уже выполнялась (инкремент при падении executor'а). */
    private Integer attemptCount;

    /** Максимум попыток до перевода в FAILED. */
    private Integer maxAttempts;

    /** Момент, не ранее которого допустим следующий повтор (UTC). */
    private OffsetDateTime nextRetryAt;

    /** Последняя зафиксированная ошибка операции. */
    private RetryError lastError;
}
