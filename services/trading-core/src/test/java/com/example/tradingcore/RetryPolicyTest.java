package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingcore.config.ServiceCommandRetryProperties;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.RetryBackoffType;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.ServiceCommandRetryPolicy;
import com.example.tradingcore.domain.command.ServiceCommandType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Политика повтора: предел, откат и поведение при ненастроенной секции.
 *
 * <p><b>Предмет — направление ошибки.</b> Политика решает, сколько раз
 * ядро постучится в площадку при живой позиции, поэтому проверяется не
 * «формула считает», а что каждая неопределённость разрешается в
 * ЗАПРЕЩАЮЩУЮ сторону: нет конфигурации — повторов нет; экспонента
 * упирается в верхнюю границу, а не переполняется
 * (docs/components/RetryPolicyService.md).
 */
class RetryPolicyTest {

    private static final ServiceCommandType COMMAND = ServiceCommandType.SUBMIT_ORDER_COMMAND;

    /**
     * Ненастроенная секция даёт ПУСТУЮ политику, а не пустоту: иначе
     * проверка бюджета падала бы в ветке учёта отказа и подменяла бы
     * исходную ошибку. Пустая политика означает «повторов нет».
     */
    @Test
    void missingConfigurationMeansNoRetries() {
        RetryPolicyService service = new RetryPolicyService(new ServiceCommandRetryProperties());

        assertThat(service.getPolicy(COMMAND)).isNotNull();
        assertThat(service.canRetry(rowWithAttempts(0), COMMAND)).isFalse();
    }

    /** Переопределение по типу команды старше умолчания. */
    @Test
    void perCommandPolicyOverridesTheDefault() {
        ServiceCommandRetryProperties properties = properties(1, RetryBackoffType.FIXED);
        properties.getPolicies().put(COMMAND, policy(9, RetryBackoffType.FIXED));
        RetryPolicyService service = new RetryPolicyService(properties);

        assertThat(service.getPolicy(COMMAND).getMaxAttempts()).isEqualTo(9);
        assertThat(service.getPolicy(ServiceCommandType.CANCEL_ORDER_COMMAND).getMaxAttempts()).isEqualTo(1);
    }

    /** Бюджет исчерпан ровно на пределе: сделанных попыток столько же, сколько разрешено. */
    @ParameterizedTest
    @CsvSource({"0,true", "1,true", "2,false", "3,false"})
    void budgetIsExhaustedAtTheLimit(Integer attempts, Boolean allowed) {
        RetryPolicyService service = new RetryPolicyService(properties(2, RetryBackoffType.FIXED));

        assertThat(service.canRetry(rowWithAttempts(attempts), COMMAND)).isEqualTo(allowed);
    }

    /**
     * Экспонента упирается в верхнюю границу и не переполняется: без
     * ограничения показателя сдвиг ушёл бы в отрицательное число и
     * обошёл бы границу — то есть повтор случился бы РАНЬШЕ, чем
     * разрешено.
     */
    @Test
    void exponentialBackoffIsCappedAndDoesNotOverflow() {
        RetryPolicyService service = new RetryPolicyService(properties(100, RetryBackoffType.EXPONENTIAL));
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        OffsetDateTime next = service.calculateNextRetryAt(rowWithAttempts(64), COMMAND);

        assertThat(next).isAfter(before);
        assertThat(Duration.between(before, next)).isLessThanOrEqualTo(Duration.ofMinutes(2).plusSeconds(1));
    }

    /** Фиксированный откат не растёт с попытками. */
    @Test
    void fixedBackoffDoesNotGrow() {
        RetryPolicyService service = new RetryPolicyService(properties(10, RetryBackoffType.FIXED));
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        OffsetDateTime next = service.calculateNextRetryAt(rowWithAttempts(7), COMMAND);

        assertThat(Duration.between(before, next)).isLessThanOrEqualTo(Duration.ofSeconds(6));
    }

    private static DealActionState rowWithAttempts(Integer attempts) {
        DealActionState row = new DealActionState();
        row.setAttemptCount(attempts);
        return row;
    }

    private static ServiceCommandRetryProperties properties(Integer maxAttempts, RetryBackoffType backoff) {
        ServiceCommandRetryProperties properties = new ServiceCommandRetryProperties();
        properties.setDefaultPolicy(policy(maxAttempts, backoff));
        return properties;
    }

    private static ServiceCommandRetryPolicy policy(Integer maxAttempts, RetryBackoffType backoff) {
        ServiceCommandRetryPolicy policy = new ServiceCommandRetryPolicy();
        policy.setMaxAttempts(maxAttempts);
        policy.setInitialDelay(Duration.ofSeconds(5));
        policy.setMaxDelay(Duration.ofMinutes(2));
        policy.setBackoff(backoff);
        return policy;
    }
}
