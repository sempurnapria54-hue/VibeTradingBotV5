package com.example.tradingcore.domain.command;

import lombok.Getter;

/**
 * Строка исполнения израсходовала бюджет попыток. Бросается диспетчером
 * ПОСЛЕ перевода строки в отказ.
 *
 * <p>Класс броска — операнд развязки выделенного обработчика прохода: в
 * отличие от контролируемого исключения интеграции, исчерпание бюджета
 * говорит не «площадка отвергла», а «мы не смогли дозвониться», и на
 * СТРАТЕГИЙНОЙ строке это не основание рвать принятый риск
 * (docs/components/DealOrchestratorJob.md).
 */
@Getter
public class RetryBudgetExhaustedException extends RuntimeException {

    /** Строка исполнения, чей бюджет исчерпан. */
    private final transient DealActionState actionState;

    /** Строка стратегийная: принятый риск на ней рвать не за что. */
    private final Boolean strategyLevel;

    public RetryBudgetExhaustedException(String message, DealActionState actionState, Boolean strategyLevel) {
        super(message);
        this.actionState = actionState;
        this.strategyLevel = strategyLevel;
    }
}
