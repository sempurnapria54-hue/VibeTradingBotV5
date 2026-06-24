package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationState;
import com.example.tradingbot.domain.command.DealFinalizationStateStatus;
import com.example.tradingbot.domain.command.RetryError;
import com.example.tradingbot.domain.command.RetryPolicyService;
import com.example.tradingbot.domain.command.Retryable;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.integration.service.ExchangeIntegrationException;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealFinalizationStateDataService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Диспетчер команд: маршрутизирует одну ServiceCommand в конкретный
 * {@link CommandExecutor} по типу, ловит ошибки и применяет
 * retry-политику к retry-anchor команды — DealActionState (action-команды)
 * либо DealFinalizationState (финализационные команды). Сам торговых
 * решений не принимает. См. docs/components/ServiceCommandExecutor.md.
 */
@Slf4j
@Service
public class ServiceCommandExecutor {

    private final Map<ServiceCommandType, CommandExecutor> registry;
    private final RetryPolicyService retryPolicyService;
    private final DealActionStateDataService dealActionStateDataService;
    private final DealFinalizationStateDataService dealFinalizationStateDataService;

    public ServiceCommandExecutor(List<CommandExecutor> executors, RetryPolicyService retryPolicyService,
                                  DealActionStateDataService dealActionStateDataService,
                                  DealFinalizationStateDataService dealFinalizationStateDataService) {
        this.registry = executors.stream().collect(toMap(CommandExecutor::supportedType, identity()));
        this.retryPolicyService = retryPolicyService;
        this.dealActionStateDataService = dealActionStateDataService;
        this.dealFinalizationStateDataService = dealFinalizationStateDataService;
    }

    public ServiceCommandExecutionResult execute(ServiceCommand command, DealContext dealContext) {
        CommandExecutor executor = registry.get(command.getType());
        if (isNull(executor)) {
            throw new IllegalStateException("No executor for command type " + command.getType());
        }
        DealActionState actionState = resolveActionState(command, dealContext);
        try {
            return executor.execute(command, actionState, dealContext);
        } catch (RuntimeException e) {
            log.error("Command execution failed [{}] dealId={}", command.getType(), command.getDealId(), e);
            return handleFailure(command, actionState, e);
        }
    }

    private DealActionState resolveActionState(ServiceCommand command, DealContext dealContext) {
        if (isNull(command.getDealActionStateId()) || isEmpty(dealContext.getActionStates())) {
            return null;
        }
        return dealContext.getActionStates().stream()
                .filter(state -> Objects.equals(command.getDealActionStateId(), state.getId()))
                .findFirst()
                .orElse(null);
    }

    private ServiceCommandExecutionResult handleFailure(ServiceCommand command, DealActionState actionState,
                                                        RuntimeException e) {
        RuntimeErrorCode errorCode = classify(e);
        if (nonNull(actionState)) {
            applyActionRetryState(command, actionState, errorCode, e);
        } else if (nonNull(command.getDealFinalizationStateId())) {
            applyFinalizationRetryState(command, errorCode, e);
        }
        return ServiceCommandExecutionResult.failure(errorCode, e.getMessage());
    }

    private RuntimeErrorCode classify(RuntimeException e) {
        // ControlledExchangeException (NotFound / Status / InvariantViolation) — внешний факт
        // говорит «не продолжать»: терминал, не ретраим (см. controlled-exchange-exceptions).
        if (e instanceof ControlledExchangeException) {
            return RuntimeErrorCode.VALIDATION_ERROR;
        }
        // Транспорт / API-сбой биржи — ретраим.
        if (e instanceof ExchangeIntegrationException) {
            return RuntimeErrorCode.EXCHANGE_ERROR;
        }
        return RuntimeErrorCode.INTERNAL_ERROR;
    }

    private void applyActionRetryState(ServiceCommand command, DealActionState actionState, RuntimeErrorCode errorCode,
                                       RuntimeException e) {
        boolean retryable = recordAttempt(actionState, command.getType(), errorCode, e);
        actionState.setStatus(retryable ? DealActionStateStatus.RETRY_PENDING : DealActionStateStatus.FAILED);
        dealActionStateDataService.save(actionState);
    }

    private void applyFinalizationRetryState(ServiceCommand command, RuntimeErrorCode errorCode, RuntimeException e) {
        DealFinalizationState state = dealFinalizationStateDataService
                .findById(command.getDealFinalizationStateId())
                .orElse(null);
        if (isNull(state)) {
            return;
        }
        boolean retryable = recordAttempt(state, command.getType(), errorCode, e);
        state.setStatus(retryable ? DealFinalizationStateStatus.RETRY_PENDING : DealFinalizationStateStatus.FAILED);
        dealFinalizationStateDataService.save(state);
    }

    private boolean recordAttempt(Retryable retryable, ServiceCommandType commandType, RuntimeErrorCode errorCode,
                                  RuntimeException e) {
        Integer attemptCount = isNull(retryable.getAttemptCount()) ? 0 : retryable.getAttemptCount();
        retryable.setAttemptCount(attemptCount + 1);
        retryable.setLastError(new RetryError(null, e.getMessage(), errorCode));
        boolean canRetry = RuntimeErrorCode.EXCHANGE_ERROR.equals(errorCode)
                && isTrue(retryPolicyService.canRetry(retryable, commandType));
        if (canRetry) {
            retryable.setNextRetryAt(retryPolicyService.calculateNextRetryAt(retryable, commandType));
        }
        return canRetry;
    }
}
