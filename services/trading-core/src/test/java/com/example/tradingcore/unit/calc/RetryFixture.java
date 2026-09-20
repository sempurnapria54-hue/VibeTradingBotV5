package com.example.tradingcore.unit.calc;

import com.example.tradingcore.config.ServiceCommandRetryProperties;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.RetryBackoffType;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.ServiceCommandRetryPolicy;
import com.example.tradingcore.domain.command.ServiceCommandType;
import java.time.Duration;

/**
 * Сборка политики повтора — групп {@code U12} и {@code U13} документа
 * `.claude/tests/cases/trading-core-calc.md`.
 *
 * <p>Конфигурация собирается кейсом как обычный объект: субстрата у
 * предмета нет, а {@code @ConfigurationProperties} — деталь потребителя.
 */
final class RetryFixture {

    /** Начальная задержка политик группы, если кейс не задаёт иной. */
    static final Duration INITIAL_DELAY = Duration.ofSeconds(5);

    /** Верхняя граница задержки политик группы, если кейс не задаёт иной. */
    static final Duration MAX_DELAY = Duration.ofMinutes(2);

    private RetryFixture() {
    }

    /** Политика с рабочими задержками и названным пределом и видом отката. */
    static ServiceCommandRetryPolicy policy(Integer maxAttempts, RetryBackoffType backoff) {
        return policy(maxAttempts, INITIAL_DELAY, MAX_DELAY, backoff);
    }

    /** Политика со всеми четырьмя величинами, названными кейсом. */
    static ServiceCommandRetryPolicy policy(Integer maxAttempts, Duration initialDelay,
                                            Duration maxDelay, RetryBackoffType backoff) {
        ServiceCommandRetryPolicy policy = new ServiceCommandRetryPolicy();
        policy.setMaxAttempts(maxAttempts);
        policy.setInitialDelay(initialDelay);
        policy.setMaxDelay(maxDelay);
        policy.setBackoff(backoff);
        return policy;
    }

    /** Служба с одним умолчанием конфигурации. */
    static RetryPolicyService service(ServiceCommandRetryPolicy defaultPolicy) {
        ServiceCommandRetryProperties properties = new ServiceCommandRetryProperties();
        properties.setDefaultPolicy(defaultPolicy);
        return new RetryPolicyService(properties);
    }

    /** Служба с умолчанием и переопределением по типу команды. */
    static RetryPolicyService service(ServiceCommandRetryPolicy defaultPolicy,
                                      ServiceCommandType commandType,
                                      ServiceCommandRetryPolicy override) {
        ServiceCommandRetryProperties properties = new ServiceCommandRetryProperties();
        properties.setDefaultPolicy(defaultPolicy);
        properties.getPolicies().put(commandType, override);
        return new RetryPolicyService(properties);
    }

    /** Строка исполнения со счётчиком попыток и снимком предела. */
    static DealActionState row(Integer attemptCount, Integer maxAttemptsSnapshot) {
        DealActionState row = new DealActionState();
        row.setAttemptCount(attemptCount);
        row.setMaxAttempts(maxAttemptsSnapshot);
        return row;
    }
}
