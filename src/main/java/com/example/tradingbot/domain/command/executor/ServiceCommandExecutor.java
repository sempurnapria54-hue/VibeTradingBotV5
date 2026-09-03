package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RetryBudgetExhaustedException;
import com.example.tradingbot.domain.command.RetryError;
import com.example.tradingbot.domain.command.RetryPolicyService;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.integration.service.ExchangeIntegrationException;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Диспетчер команд: маршрутизирует одну ServiceCommand в конкретный
 * {@link CommandExecutor} по типу, ловит ошибки и применяет
 * retry-политику к анкеру команды. Сам торговых решений не принимает.
 *
 * <p><b>Ветка учёта одна</b>, потому что анкер один — строка исполнения
 * (docs/models/domain/other/DealActionState.md). Дочистка анкера не
 * имеет вовсе, и бюджета отказов у неё поэтому тоже нет.
 *
 * <p><b>Контракт броска:</b> исполнитель переводит строку в отказ <b>и
 * затем бросает</b> — контролируемое исключение интеграции пробрасывается
 * как есть, исчерпание бюджета попыток бросается здесь. Порядок «сперва
 * отказ строки, затем бросок» часть контракта: иначе реакция поднимется
 * над строкой, не отражающей отказ
 * (docs/components/ServiceCommandExecutor.md §«Контракт броска»).
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
            ServiceCommandExecutionResult result = executor.execute(command, actionState, dealContext);
            if (isFalse(result.getSuccess())) {
                // Реджект биржи, возвращённый (не брошенный) executor'ом, тоже проходит через
                // retry/terminal-учёт — иначе анкер завис бы (сделка пере-сабмитит каждый тик).
                applyFailureAccounting(command, actionState, result.getErrorCode(), result.getMessage());
            }
            return result;
        } catch (RetryBudgetExhaustedException e) {
            // Учёт уже применён (строка переведена в отказ) — бросок идёт
            // выделенному обработчику оркестратора нетронутым.
            throw e;
        } catch (RuntimeException e) {
            log.error("Command execution failed [{}] dealId={}", command.getType(), command.getDealId(), e);
            RuntimeErrorCode errorCode = classify(e);
            applyFailureAccounting(command, actionState, errorCode, e.getMessage());
            return ServiceCommandExecutionResult.failure(errorCode, e.getMessage());
        }
    }

    /**
     * Анкер команды: строка исполнения из контекста прохода, а при её
     * отсутствии — durable-чтение по идентификатору. Второй ход несущий:
     * строка, заведённая ЭТИМ проходом уже после сборки контекста, в
     * список контекста попадает регистрацией, а строка, пришедшая иным
     * путём, — только чтением.
     */
    private DealActionState resolveActionState(ServiceCommand command, DealContext dealContext) {
        if (isNull(command.getDealActionStateId())) {
            return null;
        }
        if (isFalse(isEmpty(dealContext.getActionStates()))) {
            DealActionState fromContext = dealContext.getActionStates().stream()
                    .filter(state -> Objects.equals(command.getDealActionStateId(), state.getId()))
                    .findFirst()
                    .orElse(null);
            if (nonNull(fromContext)) {
                return fromContext;
            }
        }
        return dealActionStateDataService.findById(command.getDealActionStateId()).orElse(null);
    }

    private void applyFailureAccounting(ServiceCommand command, DealActionState actionState,
                                        RuntimeErrorCode errorCode, String message) {
        if (isNull(actionState)) {
            return;
        }
        boolean retryable = recordAttempt(actionState, command.getType(), errorCode, message);
        actionState.setStatus(retryable ? DealActionStateStatus.RETRY_PENDING : DealActionStateStatus.FAILED);
        dealActionStateDataService.save(actionState);
        if (isFalse(retryable) && RuntimeErrorCode.EXCHANGE_ERROR.equals(errorCode)) {
            // Бюджет кончился на повторяемой ошибке — это «мы не смогли дозвониться»,
            // и радиус со ступенью резолвит выделенный обработчик оркестратора.
            throw new RetryBudgetExhaustedException(
                    "Retry budget exhausted for command " + command.getType() + ": " + message,
                    actionState, isFalse(actionState.isSystem()));
        }
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

    private boolean recordAttempt(DealActionState actionState, ServiceCommandType commandType,
                                  RuntimeErrorCode errorCode, String message) {
        Integer attemptCount = isNull(actionState.getAttemptCount()) ? 0 : actionState.getAttemptCount();
        actionState.setAttemptCount(attemptCount + 1);
        actionState.setLastError(new RetryError(null, message, errorCode));
        boolean canRetry = RuntimeErrorCode.EXCHANGE_ERROR.equals(errorCode)
                && isTrue(retryPolicyService.canRetry(actionState, commandType));
        if (canRetry) {
            actionState.setNextRetryAt(retryPolicyService.calculateNextRetryAt(actionState, commandType));
        }
        return canRetry;
    }
}
