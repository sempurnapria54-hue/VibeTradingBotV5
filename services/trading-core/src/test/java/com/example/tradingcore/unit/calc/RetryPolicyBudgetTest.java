package com.example.tradingcore.unit.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingcore.config.ServiceCommandRetryProperties;
import com.example.tradingcore.domain.command.RetryBackoffType;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.ServiceCommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Политика повтора: резолв политики и бюджет попыток — группа
 * {@code U12} документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/components/RetryPolicyService.md §«Авторитет предела — политика,
 * читается живьём», §«Предел — по команде, счётчик — по исполнению»;
 * звенья Z29, Z30).
 *
 * <p><b>Предел резолвится по типу ТЕКУЩЕЙ команды, счётчик — сквозной
 * бюджет одного исполнения.</b> Поле предела на строке исполнения —
 * снимок для истории, и в решении не участвует ни одним битом.
 *
 * <p><b>Базовая сборка:</b> {@code new RetryPolicyService(properties)};
 * конфигурация собирается кейсом — политика по умолчанию плюс отображение
 * «тип команды → политика». Строка исполнения — повторяемая сущность со
 * счётчиком попыток и снимком предела.
 */
class RetryPolicyBudgetTest {

    private static final ServiceCommandType COMMAND = ServiceCommandType.SUBMIT_ORDER_COMMAND;
    private static final ServiceCommandType OTHER_COMMAND = ServiceCommandType.CANCEL_ORDER_COMMAND;

    @Test
    @DisplayName("U12.1 — секции повторов нет вовсе: пустая политика, а не пустота")
    void u12_1_aMissingSectionGivesAnEmptyPolicyRatherThanNothing() {
        RetryPolicyService service = new RetryPolicyService(new ServiceCommandRetryProperties());

        assertThatCode(() -> {
            assertThat(service.getPolicy(COMMAND))
                    .as("третья ветвь несущая: без неё проверка бюджета падала бы в ветке учёта "
                            + "отказа, подменяя исходную ошибку (Z29)")
                    .isNotNull();
            assertThat(service.canRetry(RetryFixture.row(0, null), COMMAND))
                    .as("пустая политика означает «повторов нет»")
                    .isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U12.2 — переопределение по типу старше умолчания")
    void u12_2_thePerCommandPolicyOverridesTheDefault() {
        RetryPolicyService service = RetryFixture.service(
                RetryFixture.policy(1, RetryBackoffType.FIXED),
                COMMAND, RetryFixture.policy(9, RetryBackoffType.FIXED));

        assertThat(service.getPolicy(COMMAND).getMaxAttempts()).isEqualTo(9);
    }

    @Test
    @DisplayName("U12.3 — своей политики у типа нет: берётся умолчание")
    void u12_3_theDefaultIsTakenWhenThereIsNoOverride() {
        RetryPolicyService service = RetryFixture.service(
                RetryFixture.policy(1, RetryBackoffType.FIXED),
                COMMAND, RetryFixture.policy(9, RetryBackoffType.FIXED));

        assertThat(service.getPolicy(OTHER_COMMAND).getMaxAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("U12.4 — пустой предел означает «повторов нет», а не «предел бесконечен»")
    void u12_4_anEmptyLimitForbidsRetries() {
        RetryPolicyService service =
                RetryFixture.service(RetryFixture.policy(null, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(null, null), COMMAND))
                .as("благоприятное умолчание запрещено (Z30)")
                .isFalse();
    }

    @Test
    @DisplayName("U12.5 — пустой счётчик читается как «попыток не было»")
    void u12_5_anEmptyAttemptCountMeansNoAttemptsYet() {
        RetryPolicyService service =
                RetryFixture.service(RetryFixture.policy(3, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(null, null), COMMAND)).isTrue();
    }

    @Test
    @DisplayName("U12.6 — предел 3, счётчик 2: повторять можно")
    void u12_6_aBudgetBelowTheLimitAllowsARetry() {
        RetryPolicyService service =
                RetryFixture.service(RetryFixture.policy(3, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(2, null), COMMAND)).isTrue();
    }

    @Test
    @DisplayName("U12.7 — бюджет исчерпан НА пределе: сравнение строгое")
    void u12_7_theBudgetIsExhaustedAtTheLimit() {
        RetryPolicyService service =
                RetryFixture.service(RetryFixture.policy(3, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(3, null), COMMAND)).isFalse();
    }

    @Test
    @DisplayName("U12.8 — счётчик сверх предела: повторять нельзя")
    void u12_8_aBudgetBeyondTheLimitForbidsARetry() {
        RetryPolicyService service =
                RetryFixture.service(RetryFixture.policy(3, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(4, null), COMMAND)).isFalse();
    }

    @Test
    @DisplayName("U12.9 — авторитет предела — политика: снимок на строке исполнения не читается")
    void u12_9_theSnapshotOnTheRowTakesNoPartInTheDecision() {
        RetryPolicyService service =
                RetryFixture.service(RetryFixture.policy(3, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(3, 99), COMMAND))
                .as("поле строки — снимок для истории, а не операторное значение")
                .isFalse();
    }

    @Test
    @DisplayName("U12.10 — предел резолвится по типу ТЕКУЩЕЙ команды: спрошен свой тип")
    void u12_10_theLimitIsResolvedByTheCurrentCommandType() {
        RetryPolicyService service = RetryFixture.service(
                RetryFixture.policy(2, RetryBackoffType.FIXED),
                COMMAND, RetryFixture.policy(5, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(3, null), COMMAND)).isTrue();
    }

    @Test
    @DisplayName("U12.11 — тот же вход, тип без своей политики: применимый предел меняется со звеном")
    void u12_11_theApplicableLimitChangesWithTheLink() {
        RetryPolicyService service = RetryFixture.service(
                RetryFixture.policy(2, RetryBackoffType.FIXED),
                COMMAND, RetryFixture.policy(5, RetryBackoffType.FIXED));

        assertThat(service.canRetry(RetryFixture.row(3, null), OTHER_COMMAND))
                .as("счётчик при этом остаётся сквозным")
                .isFalse();
    }

    @Test
    @DisplayName("U12.12 — тип команды пуст: берётся умолчание, исключения нет")
    void u12_12_anEmptyCommandTypeFallsBackToTheDefault() {
        RetryPolicyService service =
                RetryFixture.service(RetryFixture.policy(7, RetryBackoffType.FIXED));

        assertThatCode(() -> assertThat(service.getPolicy(null).getMaxAttempts()).isEqualTo(7))
                .as("резолв по пустому ключу отображения даёт умолчание (Z29)")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U12.13 — перебор всех входов группы: политика не возвращается пустой ни разу")
    void u12_13_thePolicyIsNeverReturnedEmpty() {
        RetryPolicyService withoutSection = new RetryPolicyService(new ServiceCommandRetryProperties());
        RetryPolicyService withDefault =
                RetryFixture.service(RetryFixture.policy(1, RetryBackoffType.FIXED));
        RetryPolicyService withOverride = RetryFixture.service(
                RetryFixture.policy(1, RetryBackoffType.FIXED),
                COMMAND, RetryFixture.policy(9, RetryBackoffType.FIXED));

        assertThat(withoutSection.getPolicy(COMMAND)).isNotNull();
        assertThat(withoutSection.getPolicy(null)).isNotNull();
        assertThat(withDefault.getPolicy(COMMAND)).isNotNull();
        assertThat(withDefault.getPolicy(OTHER_COMMAND)).isNotNull();
        assertThat(withOverride.getPolicy(COMMAND)).isNotNull();
        assertThat(withOverride.getPolicy(OTHER_COMMAND))
                .as("у резолва три ветви, и ни одна не отдаёт пустоту (Z29)")
                .isNotNull();
    }
}
