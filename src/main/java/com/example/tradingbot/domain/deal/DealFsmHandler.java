package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import java.util.Optional;

/**
 * Per-status FSM handler СДЕЛКИ. Каждый поддерживает один
 * {@link Deal.Status}; {@link DealStateMachine} маршрутизирует по нему.
 * Три роли прохода: {@link #checkEntry} (условия входа) → {@link #checkTransition}
 * (условия перехода) → {@link #handle} (рабочая логика). Петля пробует их по
 * порядку — первый непустой результат есть исход прохода, иначе {@code handle}.
 * Стадии входа и сопровождения принадлежат ТРАНШУ и здесь не
 * обрабатываются ({@link TrancheFsmHandler}). Terminal-статусы (CLOSED /
 * EMERGENCY_CLOSED) handler'ов не имеют. См.
 * docs/components/DealStateMachine.md,
 * docs/processes/fsm-execution-layering.md.
 */
public interface DealFsmHandler {

    /** Статус сделки, который обрабатывает этот handler. */
    Deal.Status supportedStatus();

    /**
     * Условия входа: предусловия работы прохода. Не выполнены → короткий
     * переход или команда-ремедиация; иначе пусто (проход идёт дальше).
     */
    default Optional<DealTransition> checkEntry(DealContext dealContext) {
        return Optional.empty();
    }

    /**
     * Условия перехода: этап завершён по подтверждённым фактам → смена статуса
     * (петля на следующей итерации возьмёт другой handler); иначе пусто (→ handle).
     */
    default Optional<DealTransition> checkTransition(DealContext dealContext) {
        return Optional.empty();
    }

    /** Рабочая логика: прогресс действия за проход, когда входа/перехода не сработали. */
    DealTransition handle(DealContext dealContext);
}
