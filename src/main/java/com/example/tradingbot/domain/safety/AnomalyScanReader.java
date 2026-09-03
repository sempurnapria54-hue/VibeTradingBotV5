package com.example.tradingbot.domain.safety;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.util.Constants;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Собирает срез счёта на тик проактивной детекции: три счёт-широких
 * выборки, разложенные по биржевому имени инструмента.
 *
 * <p><b>Контролируемое исключение сюда не ловится и наверх проходит.</b>
 * Оно поднимает биржевую ступень 2 само, безусловно для всех трёх
 * категорий (docs/rules/controlled-exchange-exceptions.md), и подменять
 * эту реакцию пометкой «проход неполон» значило бы смягчить ратифицированный
 * исход. Ловится ровно то, до чего граница не достаёт: отказ вызова, после
 * которого остальные срезы всё-таки получены.
 *
 * <p><b>Усечение страницы считается неполнотой.</b> Срез читается одной
 * страницей с явным потолком; полная страница означает «возможно, есть
 * ещё», и принять её за полный срез нельзя — детекторы молчали бы о том,
 * что не поместилось. Пагинация не строится: в фазе 1 сотня живых заявок
 * на счёте недостижима, а неполнота наблюдаема и без неё
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyScanReader {

    private final IntegrationService integrationService;

    public AnomalyScan read() {
        AnomalyScanSlice<PositionExternalSnapshot> positions = slice("positions", integrationService::getPositions);
        AnomalyScanSlice<OrderExternalSnapshot> orders = slice("orders", integrationService::getAllPendingOrders);
        AnomalyScanSlice<AlgoOrderExternalSnapshot> algos = slice("algo", integrationService::getAllPendingAlgoOrders);
        return AnomalyScan.builder()
                .positions(byInstrument(positions.getRows(), PositionExternalSnapshot::getExternalInstrumentId))
                .orders(byInstrument(orders.getRows(), OrderExternalSnapshot::getExternalInstrumentId))
                .algoOrders(byInstrument(algos.getRows(), AlgoOrderExternalSnapshot::getExternalInstrumentId))
                .complete(positions.getComplete() && orders.getComplete() && algos.getComplete())
                .build();
    }

    private <T> AnomalyScanSlice<T> slice(String name, Supplier<List<T>> supplier) {
        try {
            List<T> rows = List.copyOf(emptyIfNull(supplier.get()));
            if (rows.size() >= Constants.Okx.PENDING_PAGE_LIMIT) {
                log.warn("[anomaly] срез {} упёрся в потолок страницы ({}) — проход считается неполным",
                        name, Constants.Okx.PENDING_PAGE_LIMIT);
                return new AnomalyScanSlice<>(rows, false);
            }
            return new AnomalyScanSlice<>(rows, true);
        } catch (ControlledExchangeException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[anomaly] срез {} не добыт — проход неполон", name, e);
            return new AnomalyScanSlice<>(List.of(), false);
        }
    }

    private <T> Map<String, List<T>> byInstrument(List<T> rows, Function<T, String> address) {
        return rows.stream()
                .filter(row -> nonNull(address.apply(row)))
                .collect(groupingBy(address));
    }
}
