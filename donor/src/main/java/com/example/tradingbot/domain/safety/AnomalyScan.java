package com.example.tradingbot.domain.safety;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * Срез счёта за один тик проактивной детекции: три счёт-широких выборки,
 * разложенные по биржевому имени инструмента, плюс признак полноты
 * прохода.
 *
 * <p><b>Полнота — свойство ПРОХОДА, а не отдельного вызова.</b> Отказ
 * вызова, нарушающий контракт интеграции, поднимает биржевую ступень 2
 * сам и до джобы не доходит; сюда попадает класс, до которого граница не
 * достаёт: два среза из трёх получены, третий — нет. Такой проход не
 * ложен, он частичен, и на нём детекторы молчат
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 *
 * <p><b>Позиции хранятся списком на инструмент, а не одной записью:</b>
 * «больше одной позиции на инструмент» — самостоятельный детектор, и
 * схлопывание списка в первую запись погасило бы его признак раньше, чем
 * он его увидит.
 */
@Value
@Builder
public class AnomalyScan {

    /** Живые позиции счёта по биржевому имени инструмента. */
    Map<String, List<PositionExternalSnapshot>> positions;

    /** Живые ordinary orders счёта по биржевому имени инструмента. */
    Map<String, List<OrderExternalSnapshot>> orders;

    /** Живые algo-заявки счёта по биржевому имени инструмента. */
    Map<String, List<AlgoOrderExternalSnapshot>> algoOrders;

    /** Проход добыт целиком: все три среза получены и не усечены. */
    Boolean complete;

    public List<PositionExternalSnapshot> positionsOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(positions.get(externalInstrumentId)));
    }

    public List<OrderExternalSnapshot> ordersOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(orders.get(externalInstrumentId)));
    }

    public List<AlgoOrderExternalSnapshot> algoOrdersOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(algoOrders.get(externalInstrumentId)));
    }

    /** Биржевые имена инструментов, по которым срез несёт хоть что-то живое. */
    public Set<String> instrumentsWithLiveEntities() {
        Set<String> names = new HashSet<>();
        names.addAll(positions.keySet());
        names.addAll(orders.keySet());
        names.addAll(algoOrders.keySet());
        return names;
    }
}
