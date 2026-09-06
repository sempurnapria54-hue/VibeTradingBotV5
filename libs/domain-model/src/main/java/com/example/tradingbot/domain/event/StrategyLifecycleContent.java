package com.example.tradingbot.domain.event;

/**
 * Содержимое событий деактивации и удаления: идентичности предмета и его
 * контекста, и ничего сверх
 * (docs/architecture/contracts.md §«Содержимое несёт идентичности и то,
 * чего потребитель не дочитает сам»).
 *
 * <p><b>Дерева здесь нет, и это не пропуск:</b> определение неизменяемо,
 * и у потребителя оно уже лежит с активации — второй раз слать его
 * значило бы дублировать то, что у читателя есть.
 *
 * @param strategyInternalId          идентичность определения
 * @param exchangeAccountInternalId   счёт, на котором оно торговало
 * @param instrumentInternalId        инструмент определения
 */
public record StrategyLifecycleContent(String strategyInternalId, String exchangeAccountInternalId,
                                       String instrumentInternalId) {
}
