package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import java.util.Optional;

/**
 * Per-status FSM handler ТРАНША. Каждый поддерживает один
 * {@link DealTranche.Status}; {@link DealTrancheStateMachine}
 * маршрутизирует по нему. Три роли прохода: {@link #checkEntry} (условия
 * входа) → {@link #checkTransition} (условия перехода) → {@link #handle}
 * (рабочая логика). Петля пробует их по порядку — первый непустой
 * результат есть исход прохода, иначе {@code handle}.
 *
 * <p>Терминальный статус (CLOSED) handler'а не имеет — для него проход
 * пустой.
 *
 * <p>Обработчик получает и контекст сделки, и сам транш: стадии входа и
 * сопровождения принадлежат траншу, а инструмент, биржа, стратегия и
 * баланс — сделке, и дублировать их на транш незачем.
 *
 * <p>См. docs/components/DealTrancheStateMachine.md,
 * docs/processes/fsm-execution-layering.md.
 */
public interface TrancheFsmHandler {

    /** Статус транша, который обрабатывает этот handler. */
    DealTranche.Status supportedStatus();

    /**
     * Условия входа: предусловия работы прохода. Не выполнены → короткий
     * переход или команда-ремедиация; иначе пусто (проход идёт дальше).
     */
    default Optional<TrancheTransition> checkEntry(DealContext dealContext, DealTranche tranche) {
        return Optional.empty();
    }

    /**
     * Условия перехода: этап завершён по подтверждённым фактам → смена
     * статуса транша; иначе пусто (→ handle).
     */
    default Optional<TrancheTransition> checkTransition(DealContext dealContext, DealTranche tranche) {
        return Optional.empty();
    }

    /** Рабочая логика: прогресс действия за проход, когда входа/перехода не сработали. */
    TrancheTransition handle(DealContext dealContext, DealTranche tranche);
}
