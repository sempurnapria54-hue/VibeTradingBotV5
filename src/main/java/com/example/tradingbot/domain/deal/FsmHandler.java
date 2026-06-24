package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;

/**
 * Per-status FSM handler сделки. Каждый поддерживает один
 * {@link Deal.Status}; {@link DealStateMachine} маршрутизирует по нему.
 * Конструкция handler'а — три блока проверок (входные / рабочая логика /
 * выходные), см. docs/components/DealStateMachine.md. Terminal-статусы
 * (CLOSED / EMERGENCY_CLOSED) handler'ов не имеют.
 */
public interface FsmHandler {

    /** Статус сделки, который обрабатывает этот handler. */
    Deal.Status supportedStatus();

    /** Прогнать сделку через проверки статуса; вернуть команды и/или переход. */
    DealTransition handle(DealContext dealContext);
}
