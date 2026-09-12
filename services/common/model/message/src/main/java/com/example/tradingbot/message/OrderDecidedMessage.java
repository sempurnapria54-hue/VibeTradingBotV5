package com.example.tradingbot.message;

import java.math.BigDecimal;

/**
 * Содержимое события «решение о заявке».
 *
 * <p><b>Идентичности и то, чего потребитель не дочитает сам</b>
 * (docs/architecture/contracts.md §«Содержимое несёт идентичности»):
 * потребитель своей базы ядра не имеет и синхронно её не читает.
 * Производного здесь нет — число, выводимое из присланных, второй раз не
 * едет.
 *
 * <p><b>Идентичность транша несущая:</b> при нескольких уровнях входа
 * заявки к траншам неатрибутируемы, а {@code internalId} у транша объявлен
 * (docs/models/domain/aggregate/DealTranche.md). <b>Названное
 * ограничение:</b> эпизод транша событием не различается — счётчик эпизода
 * живёт на транше, а у заявки его нет и в ядре; дом и оживитель —
 * docs/architecture/contracts.md §«Решение о заявке несёт идентичность
 * транша».
 *
 * <p><b>Числа едут числами.</b> Цена рыночной заявки пуста законно, и
 * пустота кладётся <b>отсутствующей</b>: строка {@code "null"} завела бы
 * значение, которого в домене нет
 * (docs/architecture/contracts.md §«Пустое значение едет пустым, а не
 * текстом»).
 *
 * <p>Запись, а не {@code @Value}: у значения есть ЧИТАТЕЛЬ за
 * сериализацией, и форма обязана собираться без скрытых механизмов
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * <p><b>Форму строит маппер производителя, а не домен.</b> Прежде здесь
 * стояла фабрика {@code of(...)}, принимавшая доменную модель, — то есть
 * форма провода собиралась доменным кодом. Перевод в форму сообщения
 * принадлежит границе (.claude/rules/codestyle.md §«Слой сообщения:
 * внутренняя шина»), и делает его маппер сервиса-производителя.
 *
 * @param orderInternalId           идентичность заявки
 * @param dealInternalId            сделка заявки
 * @param dealTrancheInternalId     транш заявки — уровень входа, к которому
 *                                  она относится
 * @param exchangeAccountInternalId биржевой счёт заявки
 * @param instrumentInternalId      инструмент заявки
 * @param orderType                 бизнес-тип заявки — имя значения
 *                                  {@code Order.Type}; область значений
 *                                  домовая
 *                                  (docs/models/domain/core/Order.md), и
 *                                  здесь она не переписывается
 * @param direction                 сторона заявки — имя значения
 *                                  {@code Order.Side}; область значений
 *                                  домовая (там же), и здесь она не
 *                                  переписывается
 * @param plannedSizeContracts      объём в контрактах
 * @param plannedEntryPrice         цена размещения; пусто у рыночной заявки
 */
public record OrderDecidedMessage(String orderInternalId,
                                  String dealInternalId,
                                  String dealTrancheInternalId,
                                  String exchangeAccountInternalId,
                                  String instrumentInternalId,
                                  String orderType,
                                  String direction,
                                  BigDecimal plannedSizeContracts,
                                  BigDecimal plannedEntryPrice) {
}
