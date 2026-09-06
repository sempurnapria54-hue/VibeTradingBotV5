package com.example.tradingcore.domain.fsm;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;

/**
 * Обработчик одного статуса сделки: три блока — входные проверки,
 * рабочая логика этапа, условия перехода
 * (docs/components/DealStateMachine.md §«Конструкция обработчика: три
 * блока»).
 *
 * <p><b>Ребро в ошибку обработчик сам не пишет:</b> оно исход решения и
 * едет звеном, а обработчик гейтит эмиссию. Прямой записью ребро едет
 * только на тропах перехвата, и владеет ими петля
 * (docs/processes/fsm-execution-layering.md).
 *
 * <p><b>Границы.</b> Команд сам не строит и на биржу не ходит; статусной
 * механики не определяет — матрица рёбер и терминальные контракты живут в
 * {@link DealTransitionGate}.
 */
public interface DealHandler {

    /** Статус сделки, который ведёт этот обработчик. */
    Deal.Status handledStatus();

    /** Проход обработчика: команды и, если он нужен, целевой статус. */
    DealTransition handle(DealContext dealContext);
}
