package com.example.tradingcore.domain.safety;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * Срез биржевого счёта за один тик проактивной детекции: три
 * счёт-широких выборки, разложенные по <b>биржевому имени</b>
 * инструмента, плюс признак полноты прохода
 * (docs/components/AnomalyJob.md).
 *
 * <p><b>Адресуются строки биржевым именем, а не нашим идентификатором.</b>
 * Локального идентификатора у чужой строки не бывает по построению, а
 * именно она и есть предмет детектора «живой риск по инструменту вне
 * контура»: резолв имени в наш инструмент погасил бы находку.
 *
 * <p><b>Полнота — свойство ПРОХОДА, а не отдельного вызова.</b> Отказ,
 * нарушающий контракт интеграции, поднимает биржевую ступень 2 сам и
 * сюда не доходит; здесь живёт класс, до которого граница не достаёт: два
 * среза из трёх получены, третий — нет. Такой проход не ложен, он
 * частичен, и на нём детекторы молчат.
 *
 * <p><b>Позиции хранятся списком на инструмент, а не одной записью:</b>
 * «больше одной позиции на инструмент» — самостоятельный детектор, и
 * схлопывание списка в первую запись погасило бы его признак раньше, чем
 * он его увидит.
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией нет.
 */
@Value
@Builder
public class AnomalyScan {

    /** Живые позиции счёта по биржевому имени инструмента. */
    Map<String, List<Position>> positions;

    /** Живые обычные заявки счёта по биржевому имени инструмента. */
    Map<String, List<Order>> orders;

    /** Живые отдельные условные заявки счёта по биржевому имени инструмента. */
    Map<String, List<AlgoOrder>> algoOrders;

    /** Проход добыт целиком: все три среза получены. */
    Boolean complete;

    public List<Position> positionsOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(emptyIfNull(positions).get(externalInstrumentId)));
    }

    public List<Order> ordersOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(emptyIfNull(orders).get(externalInstrumentId)));
    }

    public List<AlgoOrder> algoOrdersOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(emptyIfNull(algoOrders).get(externalInstrumentId)));
    }

    /** Биржевые имена инструментов, по которым срез несёт хоть что-то живое. */
    public Set<String> instrumentsWithLiveEntities() {
        Set<String> names = new HashSet<>();
        names.addAll(emptyIfNull(positions).keySet());
        names.addAll(emptyIfNull(orders).keySet());
        names.addAll(emptyIfNull(algoOrders).keySet());
        return names;
    }
}
