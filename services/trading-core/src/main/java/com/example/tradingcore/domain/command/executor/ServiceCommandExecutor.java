package com.example.tradingcore.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryBudgetExhaustedException;
import com.example.tradingcore.domain.command.RetryError;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.integration.internal.api.exchange.ControlledExchangeException;
import com.example.tradingcore.integration.internal.api.exchange.CredentialsRejectedException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeIntegrationException;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Диспетчер команд: маршрутизирует одну команду в её исполнителя по типу,
 * ловит ошибки и применяет политику повтора к анкеру команды. Торговых
 * решений сам не принимает.
 *
 * <p><b>Ветка учёта одна</b>, потому что анкер один — строка исполнения
 * (docs/models/domain/other/DealActionState.md). Дочистка анкера не имеет
 * вовсе, и бюджета отказов у неё поэтому тоже нет.
 *
 * <p><b>Контракт броска:</b> строка переводится в отказ <b>и затем</b>
 * бросок идёт наружу. Порядок — часть контракта: иначе реакция поднимется
 * над строкой, не отражающей отказ
 * (docs/components/ServiceCommandExecutor.md §«Контракт броска»).
 *
 * <p><b>Контролируемое исключение ловится ИМЕНОВАННО.</b> Общий ловец
 * классифицировал бы его как нарушение инварианта и <b>вернул
 * результатом</b> — наружу оно тогда не выходит, выделенный обработчик
 * прохода его не видит, и безусловная биржевая ступень 2 не поднимается.
 * Перебор, на котором это записано: исполнителей, ходящих на площадку,
 * одиннадцать; сами ловили класс двое, и только его подкласс «статус», —
 * на девяти оставшихся он до обработчика не доходил вовсе.
 *
 * <p><b>Компенсатора «поднять ступень по коду строки» не заводится.</b>
 * Он выглядит равносильным и им не является: код нарушения инварианта
 * носит и НАШ собственный дефект, у которого исход другой
 * (docs/rules/runtime-error-classification.md), — и жёсткая ступень со
 * снятием живого риска по рынку поднималась бы на нашем баге.
 * Различитель обязан стоять там, где класс ещё известен, то есть на
 * ловце.
 *
 * <p><b>Отказ соседа по ярусу сюда не доходит и учёта не получает:</b>
 * его реакция — пропуск прохода целиком, то есть до команды дело не
 * доходит (docs/rules/runtime-error-classification.md §«Отказ соседа по
 * ярусу — свой класс, и сделку в ошибку он не уводит»).
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

    /** Исполнить одну команду прохода. */
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealContext dealContext) {
        CommandExecutor executor = registry.get(command.getType());
        if (isNull(executor)) {
            throw new IllegalStateException("No executor for command type " + command.getType());
        }
        DealActionState actionState = resolveActionState(command, dealContext);
        try {
            ServiceCommandExecutionResult result = executor.execute(command, actionState, dealContext);
            if (isFalse(result.getSuccess())) {
                // Отказ площадки, ВОЗВРАЩЁННЫЙ (а не брошенный) исполнителем, тоже
                // проходит через учёт: иначе анкер завис бы, и сделка пересылала бы
                // команду каждый тик.
                applyFailureAccounting(command, actionState, result.getErrorCode(), result.getMessage());
            }
            return result;
        } catch (RetryBudgetExhaustedException e) {
            // Учёт уже применён — строка переведена в отказ; бросок идёт
            // выделенному обработчику прохода нетронутым.
            throw e;
        } catch (ControlledExchangeException e) {
            // Свой ловец, а не общий: класс говорит «продолжать небезопасно», и
            // реакция на него — безусловная биржевая ступень 2, которую поднимает
            // проход (docs/rules/controlled-exchange-exceptions.md). Верни мы его
            // результатом — обработчик прохода класса не увидел бы.
            log.error("Controlled exchange failure [{}] dealId={}", command.getType(), command.getDealId(), e);
            failWithoutRetry(actionState, RuntimeErrorCode.VALIDATION_ERROR, e.getMessage());
            throw e;
        } catch (CredentialsRejectedException e) {
            // Класс ИЗЪЯТ из повторяемого: повтор помогает там, где отказ
            // транзиторен, а отвергнутый ключ от повторения принятым не станет —
            // бюджет истратится и кончится тем же исходом, только позже и при
            // живых позициях (docs/rules/runtime-error-classification.md).
            log.error("Credentials rejected by source [{}] dealId={}", command.getType(), command.getDealId(), e);
            failWithoutRetry(actionState, RuntimeErrorCode.EXCHANGE_ERROR, e.getMessage());
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
     * отсутствии — durable-чтение по идентификатору.
     *
     * <p>Второй ход несущий: строка, заведённая ЭТИМ проходом уже после
     * сборки контекста, попадает в список контекста регистрацией, а
     * строка, пришедшая иным путём, — только чтением.
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

    /**
     * Учёт незавершённого звена: отказа площадки и «факта не добыто».
     *
     * <p><b>Эскалацию «бюджет кончился» порождает только отказ
     * площадки.</b> У незавершённого звена без классификации площадка ни
     * при чём — исчерпанный бюджет уводит строку в отказ, и дальше её
     * подхватывает штатная ошибочная тропа действия, а не радиусная
     * реакция «мы не смогли дозвониться».
     */
    private void applyFailureAccounting(ServiceCommand command, DealActionState actionState,
                                        RuntimeErrorCode errorCode, String message) {
        if (isNull(actionState)) {
            return;
        }
        boolean retryable = recordAttempt(actionState, command.getType(), errorCode, message);
        actionState.setStatus(retryable ? DealActionStateStatus.RETRY_PENDING : DealActionStateStatus.FAILED);
        dealActionStateDataService.save(actionState);
        if (isFalse(retryable) && RuntimeErrorCode.EXCHANGE_ERROR.equals(errorCode)) {
            // Бюджет кончился на повторяемой ошибке — это «мы не смогли
            // дозвониться», и радиус со ступенью резолвит обработчик прохода.
            throw new RetryBudgetExhaustedException(
                    "Retry budget exhausted for command " + command.getType() + ": " + message,
                    actionState, isFalse(actionState.isSystem()));
        }
    }

    /**
     * Закрыть строку исполнения отказом <b>без</b> повтора.
     *
     * <p>Отдельный ход, а не ветка общего учёта: тот выводит повторяемость
     * из кода ошибки, а здесь она снята <b>природой отказа</b>, а не
     * исчерпанным бюджетом — и потому эскалации «бюджет кончился» этот
     * ход тоже не порождает
     * (docs/components/ServiceCommandExecutor.md §«У третьего строка
     * закрывается отказом БЕЗ повтора»).
     */
    private void failWithoutRetry(DealActionState actionState, RuntimeErrorCode errorCode, String message) {
        if (isNull(actionState)) {
            return;
        }
        Integer attemptCount = isNull(actionState.getAttemptCount()) ? 0 : actionState.getAttemptCount();
        actionState.setAttemptCount(attemptCount + 1);
        actionState.setLastError(new RetryError(null, message, errorCode));
        actionState.setStatus(DealActionStateStatus.FAILED);
        dealActionStateDataService.save(actionState);
    }

    /**
     * Классификация неожиданного исключения. Контролируемое сюда не
     * попадает — у него свой ловец выше.
     */
    private RuntimeErrorCode classify(RuntimeException e) {
        if (e instanceof ExchangeIntegrationException) {
            return RuntimeErrorCode.EXCHANGE_ERROR;
        }
        return RuntimeErrorCode.INTERNAL_ERROR;
    }

    /**
     * Повторяемых классов два: отказ площадки и <b>пустая
     * классификация</b> — «звено не завершено» (факт не добыт, граф не
     * полон). Неповторяемы наш собственный баг и нарушение инварианта:
     * тем же входом придёт тот же исход.
     */
    private boolean recordAttempt(DealActionState actionState, ServiceCommandType commandType,
                                  RuntimeErrorCode errorCode, String message) {
        Integer attemptCount = isNull(actionState.getAttemptCount()) ? 0 : actionState.getAttemptCount();
        actionState.setAttemptCount(attemptCount + 1);
        actionState.setLastError(new RetryError(null, message, errorCode));
        boolean canRetry = (isNull(errorCode) || RuntimeErrorCode.EXCHANGE_ERROR.equals(errorCode))
                && isTrue(retryPolicyService.canRetry(actionState, commandType));
        if (canRetry) {
            actionState.setNextRetryAt(retryPolicyService.calculateNextRetryAt(actionState, commandType));
        }
        return canRetry;
    }
}
