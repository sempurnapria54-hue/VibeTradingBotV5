package com.example.tradingbot.domain.event;

/**
 * Содержимое события «решение о заявке».
 *
 * <p><b>Идентичности и то, чего потребитель не дочитает сам</b>
 * (docs/architecture/contracts.md §«Содержимое несёт идентичности»):
 * потребитель своей базы ядра не имеет и синхронно её не читает.
 * Производного здесь нет — число, выводимое из присланных, второй раз не
 * едет.
 *
 * <p>Запись, а не {@code @Value}: у значения есть ЧИТАТЕЛЬ за
 * сериализацией, и форма обязана собираться без скрытых механизмов
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
public record OrderDecidedContent(String orderInternalId,
                                  String dealInternalId,
                                  String exchangeAccountInternalId,
                                  String instrumentInternalId,
                                  String orderType,
                                  String direction,
                                  String plannedSizeContracts,
                                  String plannedEntryPrice) {
}
