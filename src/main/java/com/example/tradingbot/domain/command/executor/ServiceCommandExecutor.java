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
import com.example.tradingbot.domain.command.RetryError;
import com.example.tradingbot.domain.command.RetryPolicyService;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Диспетчер команд: маршрутизирует одну ServiceCommand в конкретный
 * {@link CommandExecutor} по типу, ловит ошибки и применяет
 * retry-политику к DealActionState (RETRY_PENDING / FAILED). Сам
 * торговых решений не принимает. Action-state резолвится из DealContext
 * по dealActionStateId команды (null для не-action команд). См.
 * docs/components/ServiceCommandExecutor.md.
 */
@Slf4j
@Service
public class ServiceCommandExecutor {

    private final Map<ServiceCommandType, CommandExecutor> registry;
    private final RetryPolicyService retryPolicyService;
    private final DealActionStateDataService dealActionStateDataService;

    public ServiceCommandExecutor(List<CommandExecutor> executors, RetryPolicyService retryPolicyService,
                                  DealActionStateDataService dealActionStateDataService) {
        this.registry = executors.stream().collect(toMap(CommandExecutor::supportedType, identity()));
        this.retryPolicyService = retryPolicyService;
        this.dealActionStateDataService = dealActionStateDataService;
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
            applyRetryState(command, actionState, errorCode, e);
        }
        return ServiceCommandExecutionResult.failure(errorCode, e.getMessage());
    }

    private RuntimeErrorCode classify(RuntimeException e) {
        if (e instanceof ControlledExchangeException) {
            return RuntimeErrorCode.EXCHANGE_ERROR;
        }
        return RuntimeErrorCode.INTERNAL_ERROR;
    }

    private void applyRetryState(ServiceCommand command, DealActionState actionState, RuntimeErrorCode errorCode,
                                 RuntimeException e) {
        Integer attemptCount = isNull(actionState.getAttemptCount()) ? 0 : actionState.getAttemptCount();
        actionState.setAttemptCount(attemptCount + 1);
        actionState.setLastError(new RetryError(null, e.getMessage(), errorCode));
        boolean retryable = RuntimeErrorCode.EXCHANGE_ERROR.equals(errorCode)
                && isTrue(retryPolicyService.canRetry(actionState, command.getType()));
        if (retryable) {
            actionState.setNextRetryAt(retryPolicyService.calculateNextRetryAt(actionState, command.getType()));
            actionState.setStatus(DealActionStateStatus.RETRY_PENDING);
        } else {
            actionState.setStatus(DealActionStateStatus.FAILED);
        }
        dealActionStateDataService.save(actionState);
    }
}
