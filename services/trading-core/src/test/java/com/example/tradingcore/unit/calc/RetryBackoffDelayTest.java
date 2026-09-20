package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.RetryFixture.INITIAL_DELAY;
import static com.example.tradingcore.unit.calc.RetryFixture.MAX_DELAY;
import static com.example.tradingcore.unit.calc.RetryFixture.policy;
import static com.example.tradingcore.unit.calc.RetryFixture.row;
import static com.example.tradingcore.unit.calc.RetryFixture.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingcore.config.ServiceCommandRetryProperties;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.RetryBackoffType;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.ServiceCommandType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Политика повтора: откат задержки и момент следующей попытки — группа
 * {@code U13} документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/components/RetryPolicyService.md §«Модель политики»;
 * {@code RetryBackoffType}; звено Z31).
 *
 * <p><b>Часы предмет читает сам, и подменять их нечем</b>, поэтому
 * ожидание момента выражается ГРАНИЦЕЙ И ДОПУСКОМ: момент не раньше
 * «начало кейса плюс задержка» и не позже «конец кейса плюс задержка».
 * Точное значение здесь недостижимо по построению, а не по небрежности
 * (`.claude/tests/cases/trading-core-calc.md` §«Чем достаются выходы»).
 *
 * <p><b>Базовая сборка:</b> та же, что у {@code U12}; начальная задержка
 * {@code 5s}, верхняя граница {@code 2m}, если кейс не задаёт иных.
 */
class RetryBackoffDelayTest {

    private static final ServiceCommandType COMMAND = ServiceCommandType.SUBMIT_ORDER_COMMAND;

    /** Момент следующей попытки обязан лежать в окне «границы прогона плюс задержка». */
    private static void assertDelay(RetryPolicyService service, DealActionState row,
                                    Duration expected, String because) {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime next = service.calculateNextRetryAt(row, COMMAND);
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(next).as(because)
                .isAfterOrEqualTo(before.plus(expected))
                .isBeforeOrEqualTo(after.plus(expected));
    }

    @Test
    @DisplayName("U13.1 — вид отката пуст: задержка равна начальной, роста нет")
    void u13_1_anEmptyBackoffTypeDoesNotGrow() {
        RetryPolicyService service = service(policy(10, INITIAL_DELAY, MAX_DELAY, null));

        assertDelay(service, row(3, null), INITIAL_DELAY, "пустой вид отката роста не даёт (Z31)");
    }

    @Test
    @DisplayName("U13.2 — фиксированный откат не растёт с попытками")
    void u13_2_aFixedBackoffDoesNotGrow() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.FIXED));

        assertDelay(service, row(1, null), INITIAL_DELAY, "первая попытка");
        assertDelay(service, row(2, null), INITIAL_DELAY, "вторая попытка — та же задержка");
        assertDelay(service, row(5, null), INITIAL_DELAY, "пятая попытка — та же задержка");
    }

    @Test
    @DisplayName("U13.3 — экспоненциальный откат, первая попытка: показатель равен нулю")
    void u13_3_theExponentIsZeroOnTheFirstAttempt() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(1, null), INITIAL_DELAY, "начальная · 2^(попытка−1)");
    }

    @Test
    @DisplayName("U13.4 — экспоненциальный откат, вторая попытка: двойная начальная")
    void u13_4_theSecondAttemptDoublesTheInitialDelay() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(2, null), INITIAL_DELAY.multipliedBy(2), "двойная начальная");
    }

    @Test
    @DisplayName("U13.5 — экспоненциальный откат, третья попытка: четверная начальная")
    void u13_5_theThirdAttemptQuadruplesTheInitialDelay() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(3, null), INITIAL_DELAY.multipliedBy(4), "четверная начальная");
    }

    @Test
    @DisplayName("U13.6 — пустой счётчик читается как первая попытка")
    void u13_6_anEmptyAttemptCountIsReadAsTheFirstAttempt() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(null, null), INITIAL_DELAY, "задержка равна начальной (Z31)");
    }

    @Test
    @DisplayName("U13.7 — нулевой счётчик: показатель отрицательным не бывает")
    void u13_7_aZeroAttemptCountDoesNotGoBelowTheFirstAttempt() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(0, null), INITIAL_DELAY, "нижняя отсечка показателя (Z31)");
    }

    @Test
    @DisplayName("U13.8 — отрицательный счётчик: та же нижняя отсечка")
    void u13_8_aNegativeAttemptCountHitsTheSameFloor() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(-3, null), INITIAL_DELAY, "та же нижняя отсечка (Z31)");
    }

    @Test
    @DisplayName("U13.9 — экспоненциальный рост связывается верхней границей")
    void u13_9_theExponentialGrowthIsCappedByTheUpperBound() {
        RetryPolicyService service = service(policy(100, RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(10, null), MAX_DELAY, "масштабированная величина выше потолка");
    }

    @Test
    @DisplayName("U13.10 — потолок пуст: рост не связывается ничем")
    void u13_10_anEmptyCapLeavesTheGrowthUnbounded() {
        RetryPolicyService service = service(policy(100, INITIAL_DELAY, null,
                RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(4, null), INITIAL_DELAY.multipliedBy(8), "восьмикратная начальная");
    }

    @Test
    @DisplayName("U13.11 — показатель ограничен: сдвиг в отрицательное не уходит, исключения нет")
    void u13_11_theExponentIsBoundedAndDoesNotOverflow() {
        RetryPolicyService service = service(policy(100, RetryBackoffType.EXPONENTIAL));

        assertThatCode(() -> assertDelay(service, row(1000, null), MAX_DELAY,
                "без ограничения показателя сдвиг обошёл бы верхнюю границу (Z31)"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U13.12 — фиксированный откат потолка не применяет")
    void u13_12_aFixedBackoffIgnoresTheCap() {
        RetryPolicyService service = service(policy(10, Duration.ofMinutes(5), Duration.ofMinutes(1),
                RetryBackoffType.FIXED));

        assertDelay(service, row(3, null), Duration.ofMinutes(5),
                "верхняя граница объявлена величиной экспоненциального отката (Z31)");
    }

    @Test
    @DisplayName("U13.13 — пустая начальная задержка читается нулём, а не роняет расчёт")
    void u13_13_anEmptyInitialDelayIsReadAsZero() {
        RetryPolicyService service = service(policy(10, null, MAX_DELAY,
                RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(3, null), Duration.ZERO, "момент — текущий (Z31)");
    }

    @Test
    @DisplayName("U13.14 — политика пуста целиком: задержка нулевая, исключения нет")
    void u13_14_anEmptyPolicyGivesAZeroDelay() {
        RetryPolicyService service = new RetryPolicyService(new ServiceCommandRetryProperties());

        assertThatCode(() -> assertDelay(service, row(3, null), Duration.ZERO,
                "пустая политика момент не роняет (Z29, Z31)"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U13.15 — два вызова подряд: момент строится от часов процесса")
    void u13_15_theMomentIsBuiltFromTheProcessClock() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.FIXED));
        DealActionState row = row(1, null);

        OffsetDateTime first = service.calculateNextRetryAt(row, COMMAND);
        OffsetDateTime second = service.calculateNextRetryAt(row, COMMAND);

        assertThat(second)
                .as("а не от поля строки исполнения")
                .isAfterOrEqualTo(first);
    }

    @Test
    @DisplayName("U13.16 — строка исполнения не меняется: резолвер её сам не двигает")
    void u13_16_theExecutionRowIsLeftUntouched() {
        RetryPolicyService service = service(policy(10, RetryBackoffType.EXPONENTIAL));
        DealActionState row = row(2, 99);

        service.calculateNextRetryAt(row, COMMAND);

        assertThat(row.getAttemptCount()).isEqualTo(2);
        assertThat(row.getMaxAttempts()).isEqualTo(99);
        assertThat(row.getNextRetryAt()).as("момент пишет вызывающий, а не политика").isNull();
    }

    @Test
    @DisplayName("U13.17 — масштабированная величина РАВНА потолку: связывание исход не меняет")
    void u13_17_aScaledDelayEqualToTheCapStaysAtTheCap() {
        RetryPolicyService service = service(policy(100, INITIAL_DELAY, Duration.ofSeconds(20),
                RetryBackoffType.EXPONENTIAL));

        assertDelay(service, row(3, null), Duration.ofSeconds(20),
                "вторая сторона границы к U13.9 и U13.11: сравнение предъявлено обеими (Z31)");
    }
}
