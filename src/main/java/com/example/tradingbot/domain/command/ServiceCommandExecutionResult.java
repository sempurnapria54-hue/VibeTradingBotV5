package com.example.tradingbot.domain.command;

import lombok.Value;

/**
 * Результат исполнения одной ServiceCommand: успех или классификация
 * ошибки для retry-политики. Сущность и DealActionState executor
 * обновляет сам; результат сигналит исход диспетчеру
 * (ServiceCommandExecutor → RetryPolicyService). RVO. См.
 * docs/components/ServiceCommandExecutor.md.
 */
@Value
public class ServiceCommandExecutionResult {

    /** Команда исполнена успешно. */
    Boolean success;

    /** Классификация ошибки (null при success); определяет retryable-политику. */
    RuntimeErrorCode errorCode;

    /** Сообщение об ошибке (null при success). */
    String message;

    /** Успешный результат. */
    public static ServiceCommandExecutionResult ok() {
        return new ServiceCommandExecutionResult(Boolean.TRUE, null, null);
    }

    /** Результат-ошибка с классификацией. */
    public static ServiceCommandExecutionResult failure(RuntimeErrorCode errorCode, String message) {
        return new ServiceCommandExecutionResult(Boolean.FALSE, errorCode, message);
    }
}
