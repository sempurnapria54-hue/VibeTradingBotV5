package com.example.tradingcore.domain.safety;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.integration.exchange.ControlledExchangeException;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Собирает срез биржевого счёта на тик проактивной детекции: три
 * счёт-широких выборки, разложенные по биржевому имени инструмента
 * (docs/components/AnomalyJob.md §«Проход идёт по биржевым счетам»).
 *
 * <p><b>Поштучного обхода по инструментам нет ни у одного среза:</b>
 * поштучное чтение росло бы с числом инструментов, выбирая лимит у той
 * торговой петли, ради которой всё и работает.
 *
 * <p><b>Контролируемое исключение сюда не ловится и наверх проходит.</b>
 * Оно поднимает биржевую ступень 2 само, безусловно для всех трёх
 * категорий (docs/rules/controlled-exchange-exceptions.md), и подменять
 * эту реакцию пометкой «проход неполон» значило бы смягчить
 * ратифицированный исход. Ловится ровно то, до чего граница не достаёт:
 * отказ вызова, после которого остальные срезы всё-таки получены.
 *
 * <p><b>Нулевая позиция живой не считается.</b> Источник отдаёт строку с
 * нулевым размером по уже закрытой позиции, и нормализация одна на всех
 * потребителей: иначе детектор чужого риска объявил бы чужой закрытую
 * позицию, детектор непроэнфорсенной ступени рапортовал бы о живых
 * сущностях при пустом счёте, а детектор хвостов молчал бы ровно в своей
 * популяции — «позиции нет, а заявки есть».
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyScanReader {

    private final ExchangeOperationsClient exchangeOperationsClient;

    /** Срез одного счёта: три выборки плюс признак полноты прохода. */
    public AnomalyScan read(String accountInternalId) {
        AnomalyScanSlice<Position> positions = slice("positions",
                () -> exchangeOperationsClient.getPositions(accountInternalId));
        AnomalyScanSlice<Order> orders = slice("orders",
                () -> exchangeOperationsClient.getAllPendingOrders(accountInternalId));
        AnomalyScanSlice<AlgoOrder> algoOrders = slice("algo",
                () -> exchangeOperationsClient.getAllPendingAlgoOrders(accountInternalId));
        return AnomalyScan.builder()
                .positions(byInstrument(livePositions(positions.getRows()),
                        Position::getExternalInstrumentId))
                .orders(byInstrument(orders.getRows(), Order::getExternalInstrumentId))
                .algoOrders(byInstrument(algoOrders.getRows(), AlgoOrder::getExternalInstrumentId))
                .complete(positions.getComplete() && orders.getComplete() && algoOrders.getComplete())
                .build();
    }

    private <T> AnomalyScanSlice<T> slice(String name, Supplier<List<T>> supplier) {
        try {
            return new AnomalyScanSlice<>(List.copyOf(emptyIfNull(supplier.get())), true);
        } catch (ControlledExchangeException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Anomaly scan slice {} is not harvested: the pass is incomplete", name, e);
            return new AnomalyScanSlice<>(List.of(), false);
        }
    }

    /** Строки закрытых позиций (нулевой размер) из среза выбывают. */
    private List<Position> livePositions(List<Position> rows) {
        return rows.stream()
                .filter(row -> nonNull(row.getExternalSize()))
                .filter(row -> row.getExternalSize().compareTo(BigDecimal.ZERO) > 0)
                .collect(toList());
    }

    /**
     * Раскладка строк по биржевому имени инструмента. Строка без имени в
     * раскладку не попадает: адресовать её нечем, и приписывание её
     * первому попавшемуся инструменту было бы выдумкой.
     */
    private <T> Map<String, List<T>> byInstrument(List<T> rows, Function<T, String> address) {
        return rows.stream()
                .filter(row -> nonNull(address.apply(row)))
                .collect(groupingBy(address));
    }
}
