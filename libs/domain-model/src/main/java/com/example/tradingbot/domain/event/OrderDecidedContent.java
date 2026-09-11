package com.example.tradingbot.domain.event;

import static com.example.tradingbot.domain.util.EnumNames.name;

import com.example.tradingbot.domain.model.core.order.Order;
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
public record OrderDecidedContent(String orderInternalId,
                                  String dealInternalId,
                                  String dealTrancheInternalId,
                                  String exchangeAccountInternalId,
                                  String instrumentInternalId,
                                  String orderType,
                                  String direction,
                                  BigDecimal plannedSizeContracts,
                                  BigDecimal plannedEntryPrice) {

    /**
     * Содержимое решения по заявке и идентичностям её радиуса.
     *
     * <p>Фабрика держит приведение перечней к именам и <b>пустую цену</b>:
     * у рыночной заявки цены нет законно, и {@code String.valueOf} завёл бы
     * на её месте литерал {@code "null"}.
     */
    public static OrderDecidedContent of(Order order, String dealInternalId,
                                         String dealTrancheInternalId,
                                         String exchangeAccountInternalId,
                                         String instrumentInternalId) {
        return new OrderDecidedContent(order.getInternalId(),
                dealInternalId,
                dealTrancheInternalId,
                exchangeAccountInternalId,
                instrumentInternalId,
                name(order.getType()),
                name(order.getSide()),
                order.getSize(),
                order.getPrice());
    }
}
