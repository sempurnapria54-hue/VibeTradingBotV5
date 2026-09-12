package com.example.tradingbot.message;

/**
 * Содержимое событий деактивации и удаления: идентичности предмета, его
 * контекста и актор перехода — и ничего сверх
 * (docs/architecture/contracts.md §«Содержимое несёт идентичности и то,
 * чего потребитель не дочитает сам»).
 *
 * <p><b>Дерева здесь нет, и это не пропуск:</b> определение неизменяемо,
 * и у потребителя оно уже лежит с активации — второй раз слать его
 * значило бы дублировать то, что у читателя есть.
 *
 * <p><b>Актор едет содержимым, потому что у обоих классов есть ручная
 * тропа:</b> остановку и удаление определения инициирует человек
 * (docs/spec/event-actor-presence.json). Область значений — <b>класс</b>,
 * а не идентификатор пользователя
 * (docs/models/domain/other/Auditable.md §«Область значений актора»).
 *
 * @param strategyInternalId          идентичность определения
 * @param exchangeAccountInternalId   счёт, на котором оно торговало
 * @param instrumentInternalId        инструмент определения
 * @param actor                       кто инициировал переход: имя предъявленного
 *                                    принципала либо класс собственного прохода
 */
public record StrategyLifecycleMessage(String strategyInternalId, String exchangeAccountInternalId,
                                       String instrumentInternalId, String actor) {
}
