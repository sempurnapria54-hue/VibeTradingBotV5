package com.example.tradingcore.domain.safety;

import static com.example.tradingcore.util.PositionMarginMode.isForeign;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * нарушающий контракт интеграции, сюда не доходит: его исход — биржевая
 * ступень 2, и поднимает её ловец прохода у {@code AnomalyJob}. Здесь живёт
 * класс, до которого граница не достаёт: два
 * среза из трёх получены, третий — нет. Такой проход не ложен, он
 * частичен, и на нём детекторы молчат.
 *
 * <p><b>Позиции хранятся списком на инструмент, а не одной записью:</b>
 * «больше одной позиции на инструмент» — самостоятельный детектор, и
 * схлопывание списка в первую запись погасило бы его признак раньше, чем
 * он его увидит.
 *
 * <p><b>Запись позиции читается нашей только в режиме маржи контура.</b>
 * Площадка держит изолированную и кросс-позицию одного инструмента рядом,
 * отдельными записями, и различает их только режим записи. Популяции,
 * читающие позицию как нашу (восстановительная тропа, хвосты заявок, счёт
 * кардинальности), берут записи режима контура; запись иного режима —
 * сущность, которую система не создавала, и её наблюдатель — детектор
 * позиции на инструменте контура (docs/components/AnomalyJob.md, разбор
 * {@code A3}). Признак «живые сущности по инструменту» видит обе: это риск
 * на счёте.
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

    /** Живые позиции режима маржи контура по биржевому имени — те, что читаются нашими. */
    public List<Position> contourPositionsOf(String externalInstrumentId) {
        return positionsOf(externalInstrumentId).stream()
                .filter(position -> isFalse(isForeign(position)))
                .toList();
    }

    /** Живые позиции иного режима маржи по биржевому имени — их открыла не наша заявка. */
    public List<Position> foreignMarginModePositionsOf(String externalInstrumentId) {
        return positionsOf(externalInstrumentId).stream()
                .filter(position -> isTrue(isForeign(position)))
                .toList();
    }

    public List<Order> ordersOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(emptyIfNull(orders).get(externalInstrumentId)));
    }

    public List<AlgoOrder> algoOrdersOf(String externalInstrumentId) {
        return List.copyOf(emptyIfNull(emptyIfNull(algoOrders).get(externalInstrumentId)));
    }

    /**
     * Строки среза по одному биржевому имени — внешний снимок находки,
     * найденной по этому адресу (docs/models/domain/other/AnomalyReport.md
     * §Структура). Позиции списком: признак «больше одной позиции» иначе
     * пропал бы из снимка ровно того отчёта, который он вызвал.
     */
    public Map<String, Object> observedRowsOf(String externalInstrumentId) {
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("instrumentExternalId", externalInstrumentId);
        observed.put("positions", positionsOf(externalInstrumentId));
        observed.put("pendingOrders", ordersOf(externalInstrumentId));
        observed.put("pendingAlgoOrders", algoOrdersOf(externalInstrumentId));
        return observed;
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
