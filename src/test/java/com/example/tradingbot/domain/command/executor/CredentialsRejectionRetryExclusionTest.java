package com.example.tradingbot.domain.command.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RetryBudgetExhaustedException;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.integration.service.CredentialsRejectedException;
import com.example.tradingbot.integration.service.ExchangeIntegrationException;
import com.example.tradingbot.domain.command.RetryPolicyService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Изъятие отказа кредов из ретраибельного на границе исполнения
 * (docs/rules/runtime-error-classification.md §«Отказ источника в наших кредах
 * изъят из ретраибельного»).
 *
 * <p><b>Что охраняется.</b> Повтор помогает там, где отказ транзиторен;
 * отвергнутый ключ от повторения принятым не станет — бюджет истратится и
 * кончится тем же исходом, только позже и <b>при живых позициях</b>. Плюс
 * второе: бросок обязан уйти наружу, потому что ступень запрашивает граница
 * исполнения прохода, а не эта.
 *
 * <p><b>Контроль обязателен:</b> обычный сбой интеграции на тех же операндах
 * повтор получает. Без контроля проба прошла бы и на конфигурации, где не
 * ретраится вообще ничего.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredentialsRejectionRetryExclusionTest {

    private static final Long ACTION_STATE_ID = 42L;

    @Mock
    private RetryPolicyService retryPolicyService;

    @Mock
    private DealActionStateDataService dealActionStateDataService;

    @Test
    @DisplayName("Отказ кредов: строка в отказ без повтора, бросок уходит наружу")
    void credentialsRejectionFailsRowWithoutRetryAndRethrows() {
        DealActionState actionState = actionState();
        ServiceCommandExecutor executor = executorThrowing(
                new CredentialsRejectedException("OKX rejected our credentials code=50113"), actionState);

        assertThatThrownBy(() -> executor.execute(command(), context()))
                .as("ступень запрашивает граница исполнения прохода — бросок обязан дойти до неё")
                .isInstanceOf(CredentialsRejectedException.class);

        assertThat(actionState.getStatus())
                .as("строка обязана быть закрыта отказом, а не оставлена ждать повтора")
                .isEqualTo(DealActionStateStatus.FAILED);
        assertThat(actionState.getNextRetryAt())
                .as("повтора у отказа кредов нет: он не транзиторен по природе")
                .isNull();
        assertThat(actionState.getLastError().getType())
                .as("класс остаётся биржевым: это не баг приложения и не нарушение инварианта")
                .isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
        verify(dealActionStateDataService).save(actionState);
        // Бюджет НЕ трогается: повторяемость снята природой отказа, а не
        // исчерпанием бюджета, — значит и эскалации «бюджет кончился» нет.
        verify(retryPolicyService, never()).canRetry(any(), any());
    }

    @Test
    @DisplayName("Контроль: обычный сбой интеграции на тех же операндах повтор получает")
    void ordinaryIntegrationFailureStillRetries() {
        DealActionState actionState = actionState();
        when(retryPolicyService.canRetry(any(), any())).thenReturn(true);
        ServiceCommandExecutor executor = executorThrowing(
                new ExchangeIntegrationException("OKX transport error"), actionState);

        ServiceCommandExecutionResult result = executor.execute(command(), context());

        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
        assertThat(actionState.getStatus())
                .as("транзиторный сбой обязан ждать повтора — иначе проба выше ничего не различает")
                .isEqualTo(DealActionStateStatus.RETRY_PENDING);
    }

    @Test
    @DisplayName("Отказ кредов не превращается в исчерпание бюджета попыток")
    void credentialsRejectionIsNotBudgetExhaustion() {
        DealActionState actionState = actionState();
        ServiceCommandExecutor executor = executorThrowing(
                new CredentialsRejectedException("OKX rejected our credentials code=50119"), actionState);

        // Тропа «бюджет кончился» ведёт к другому обработчику оркестратора —
        // тому, что уводит системную строку в ошибку, а ступени не поднимает.
        assertThatThrownBy(() -> executor.execute(command(), context()))
                .isNotInstanceOf(RetryBudgetExhaustedException.class);
    }

    private ServiceCommandExecutor executorThrowing(RuntimeException failure, DealActionState actionState) {
        CommandExecutor failing = new CommandExecutor() {
            @Override
            public ServiceCommandType supportedType() {
                return ServiceCommandType.REFRESH_ORDER_COMMAND;
            }

            @Override
            public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState state,
                                                         DealContext dealContext) {
                throw failure;
            }
        };
        when(dealActionStateDataService.findById(ACTION_STATE_ID)).thenReturn(java.util.Optional.of(actionState));
        return new ServiceCommandExecutor(List.of(failing), retryPolicyService, dealActionStateDataService);
    }

    private DealActionState actionState() {
        DealActionState state = new DealActionState();
        state.setId(ACTION_STATE_ID);
        state.setStatus(DealActionStateStatus.SUBMITTED);
        state.setAttemptCount(0);
        return state;
    }

    private ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ORDER_COMMAND)
                .dealId(1L)
                .dealActionStateId(ACTION_STATE_ID)
                .build();
    }

    private DealContext context() {
        return DealContext.builder().build();
    }
}
