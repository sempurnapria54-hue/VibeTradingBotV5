package com.example.tradingcore.domain.fsm;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;

/**
 * Обработчик одного статуса транша: три блока — входные проверки,
 * рабочая логика этапа, условия перехода
 * (docs/components/DealTrancheStateMachine.md §«Конструкция обработчика —
 * та же, что у сделки»).
 *
 * <p><b>Область — свой транш.</b> Операнды обработчика — заявки, защиты и
 * экспозиция ЭТОГО транша; соседние транши в них не входят. Единственное
 * исключение — окно сворачивания сделки: приписанное закрытие уровня
 * сделки зависит от соседей, и раскладывает его правило сопоставления
 * (docs/models/domain/aggregate/DealTranche.md).
 *
 * <p><b>Границы.</b> Команд сам не исполняет и на биржу не ходит;
 * статусной механики не определяет — матрица рёбер живёт в
 * {@link TrancheTransitionGate}; терминала сделки не ставит и потолков
 * риска не считает.
 */
public interface DealTrancheHandler {

    /** Статус транша, который ведёт этот обработчик. */
    DealTranche.Status handledStatus();

    /** Проход обработчика: команды и, если он нужен, целевой статус. */
    TrancheTransition handle(DealContext dealContext, DealTranche tranche);
}
