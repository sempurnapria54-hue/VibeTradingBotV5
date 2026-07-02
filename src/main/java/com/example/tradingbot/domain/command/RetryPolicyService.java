package com.example.tradingbot.domain.command;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.config.ServiceCommandRetryProperties;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Техническая политика повтора команд (не часть торговой стратегии):
 * выбор политики по типу, можно ли ещё повторять, расчёт момента
 * следующего повтора по backoff. Политики — из конфига
 * (ServiceCommandRetryProperties). См.
 * docs/components/RetryPolicyService.md.
 */
@Service
@RequiredArgsConstructor
public class RetryPolicyService {

    private final ServiceCommandRetryProperties properties;

    /** Политика для типа команды (fallback на default). */
    public ServiceCommandRetryPolicy getPolicy(ServiceCommandType commandType) {
        return properties.getPolicies().getOrDefault(commandType, properties.getDefaultPolicy());
    }

    /** Можно ли ещё повторять (attemptCount < maxAttempts). */
    public Boolean canRetry(Retryable retryable, ServiceCommandType commandType) {
        Integer maxAttempts = getPolicy(commandType).getMaxAttempts();
        Integer attemptCount = retryable.getAttemptCount();
        return nonNull(maxAttempts) && (isNull(attemptCount) || attemptCount < maxAttempts);
    }

    /** Момент следующего повтора (now + backoff-задержка). */
    public OffsetDateTime calculateNextRetryAt(Retryable retryable, ServiceCommandType commandType) {
        ServiceCommandRetryPolicy policy = getPolicy(commandType);
        Duration delay = computeDelay(policy, retryable.getAttemptCount());
        return OffsetDateTime.now(ZoneOffset.UTC).plus(delay);
    }

    private Duration computeDelay(ServiceCommandRetryPolicy policy, Integer attemptCount) {
        Duration initial = nonNull(policy.getInitialDelay()) ? policy.getInitialDelay() : Duration.ZERO;
        if (isNull(policy.getBackoff()) || RetryBackoffType.FIXED.equals(policy.getBackoff())) {
            return initial;
        }
        int attempts = isNull(attemptCount) ? 1 : Math.max(1, attemptCount);
        // Экспонента ограничена, чтобы сдвиг не переполнил long в отрицательное (обход maxDelay-cap);
        // множитель 2^30 заведомо упирается в maxDelay для любого реального конфига backoff.
        long multiplier = 1L << Math.min(attempts - 1, 30);
        Duration scaled = initial.multipliedBy(multiplier);
        Duration maxDelay = policy.getMaxDelay();
        return nonNull(maxDelay) && scaled.compareTo(maxDelay) > 0 ? maxDelay : scaled;
    }
}
