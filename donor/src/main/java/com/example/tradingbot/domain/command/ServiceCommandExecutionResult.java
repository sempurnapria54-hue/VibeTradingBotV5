package com.example.tradingbot.domain.command;

import com.example.tradingbot.domain.safety.HoldSignal;
import lombok.Value;

/**
 * Результат исполнения одной ServiceCommand: успех или классификация
 * ошибки для retry-политики, плюс — при необходимости — затребованная
 * ступень safety. Сущность и DealActionState executor обновляет сам;
 * результат сигналит исход диспетчеру
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

    /**
     * Затребованная исполнителем ступень safety; {@code null} — не
     * затребована.
     *
     * <p><b>Исполнитель ступень ЗАТРЕБУЕТ, а поднимает её проход</b> — так
     * же, как переход сделки: исход прохода есть намерение, а не право
     * (docs/processes/fsm-execution-layering.md). Прямой вызов исполнителя
     * блокировки из звена и невозможен по построению: исполнитель
     * блокировки ведёт снятие риска тем же диспетчером команд, который
     * зовёт звено, — зависимость замкнулась бы в цикл.
     */
    HoldSignal holdSignal;

    /** Успешный результат. */
    public static ServiceCommandExecutionResult ok() {
        return new ServiceCommandExecutionResult(Boolean.TRUE, null, null, null);
    }

    /** Успешный результат, затребовавший ступень safety. */
    public static ServiceCommandExecutionResult okWithHold(HoldSignal holdSignal) {
        return new ServiceCommandExecutionResult(Boolean.TRUE, null, null, holdSignal);
    }

    /** Результат-ошибка с классификацией. */
    public static ServiceCommandExecutionResult failure(RuntimeErrorCode errorCode, String message) {
        return new ServiceCommandExecutionResult(Boolean.FALSE, errorCode, message, null);
    }
}
