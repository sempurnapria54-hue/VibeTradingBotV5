package com.example.tradingbot.domain.command;

import lombok.Getter;

/**
 * Строка исполнения израсходовала бюджет попыток. Бросается исполнителем
 * команд ПОСЛЕ перевода строки в отказ.
 *
 * <p>Класс броска — операнд развязки выделенного обработчика оркестратора
 * (docs/components/DealOrchestratorJob.md): в отличие от контролируемого
 * исключения интеграции, исчерпание бюджета говорит не «биржа отвергла», а
 * «мы не смогли дозвониться», и на СТРАТЕГИЙНОЙ строке это не основание
 * рвать принятый риск.
 */
@Getter
public class RetryBudgetExhaustedException extends RuntimeException {

    /** Строка исполнения, чей бюджет исчерпан. */
    private final transient DealActionState actionState;

    /** Исполнение стратегийного действия (у системного — false). */
    private final Boolean strategyLevel;

    public RetryBudgetExhaustedException(String message, DealActionState actionState, Boolean strategyLevel) {
        super(message);
        this.actionState = actionState;
        this.strategyLevel = strategyLevel;
    }
}
