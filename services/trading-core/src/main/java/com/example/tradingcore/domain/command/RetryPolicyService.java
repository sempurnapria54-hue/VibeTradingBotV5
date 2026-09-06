package com.example.tradingcore.domain.command;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingcore.config.ServiceCommandRetryProperties;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Техническая политика повтора команд — не часть торговой стратегии:
 * выбор политики по типу, можно ли ещё повторять, момент следующей
 * попытки.
 *
 * <p><b>Предел резолвится по типу ТЕКУЩЕЙ команды, счётчик — сквозной
 * бюджет одного исполнения.</b> У многозвенного системного действия
 * применимый предел меняется со звеном, и это намеренно: звенья
 * однородны по цене отказа, а бюджет у действия один
 * (docs/components/RetryPolicyService.md).
 *
 * <p>Строки исполнения сам не двигает и исход повтора не интерпретирует.
 */
@Service
@RequiredArgsConstructor
public class RetryPolicyService {

    /**
     * Показатель экспоненты ограничен: без ограничения сдвиг переполнил бы
     * знаковое число в отрицательное и обошёл бы верхнюю границу задержки.
     * Значение заведомо упирается в неё при любой реальной конфигурации.
     */
    private static final int MAX_BACKOFF_SHIFT = 30;

    private final ServiceCommandRetryProperties properties;

    /**
     * Политика для типа команды: своя, иначе умолчание конфигурации,
     * иначе <b>пустая</b>.
     *
     * <p>Третья ветвь несущая: без неё отсутствующая секция конфигурации
     * давала бы пустоту, и проверка бюджета падала бы в ветке учёта
     * отказа — подменяя исходную ошибку. Пустая политика означает
     * «повторов нет» и ведёт строку в отказ, то есть ошибается в
     * запрещающую сторону.
     */
    public ServiceCommandRetryPolicy getPolicy(ServiceCommandType commandType) {
        ServiceCommandRetryPolicy policy = properties.getPolicies()
                .getOrDefault(commandType, properties.getDefaultPolicy());
        return nonNull(policy) ? policy : new ServiceCommandRetryPolicy();
    }

    /** Можно ли ещё повторять: попыток сделано меньше предела. */
    public Boolean canRetry(Retryable retryable, ServiceCommandType commandType) {
        Integer maxAttempts = getPolicy(commandType).getMaxAttempts();
        Integer attemptCount = retryable.getAttemptCount();
        return nonNull(maxAttempts) && (isNull(attemptCount) || attemptCount < maxAttempts);
    }

    /** Момент следующей попытки: сейчас плюс задержка отката. */
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
        long multiplier = 1L << Math.min(attempts - 1, MAX_BACKOFF_SHIFT);
        Duration scaled = initial.multipliedBy(multiplier);
        Duration maxDelay = policy.getMaxDelay();
        return nonNull(maxDelay) && scaled.compareTo(maxDelay) > 0 ? maxDelay : scaled;
    }
}
