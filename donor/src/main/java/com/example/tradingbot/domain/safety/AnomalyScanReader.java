package com.example.tradingbot.domain.safety;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.integration.service.IntegrationService;
import java.math.BigDecimal;
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
 * <p><b>Усечение страницы считается неполнотой, и меряет его сторона
 * источника.</b> Потолок задан вызову, поэтому усечение видно там, где
 * страница выдана: граница интеграции бросает на усечённой странице, а
 * читатель засчитывает это неполнотой наравне с неполученным срезом.
 * Здесь потолок не меряется — у читателя нет операнда «сколько страниц
 * было выдано», а склейка нескольких семей превышала бы потолок штатно
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 *
 * <p><b>Нулевая позиция живой не считается.</b> Источник отдаёт строку с
 * {@code pos = 0} по закрытой позиции (docs/integrations/okx/contracts/
 * position.md), и без нормализации она читалась бы живым риском: `A2`
 * снёс бы биржу за закрытую позицию, а `A9` молчал бы ровно в своей
 * популяции («позиции нет, а заявки есть»). Нормализация одна на всех
 * потребителей — иначе каждый детектор понимал бы «живую позицию»
 * по-своему.
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
                .positions(byInstrument(livePositions(positions.getRows()),
                        PositionExternalSnapshot::getExternalInstrumentId))
                .orders(byInstrument(orders.getRows(), OrderExternalSnapshot::getExternalInstrumentId))
                .algoOrders(byInstrument(algos.getRows(), AlgoOrderExternalSnapshot::getExternalInstrumentId))
                .complete(positions.getComplete() && orders.getComplete() && algos.getComplete())
                .build();
    }

    private <T> AnomalyScanSlice<T> slice(String name, Supplier<List<T>> supplier) {
        try {
            return new AnomalyScanSlice<>(List.copyOf(emptyIfNull(supplier.get())), true);
        } catch (ControlledExchangeException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[anomaly] срез {} не добыт — проход неполон", name, e);
            return new AnomalyScanSlice<>(List.of(), false);
        }
    }

    /** Строки закрытых позиций ({@code pos = 0}) из среза выбывают. */
    private List<PositionExternalSnapshot> livePositions(List<PositionExternalSnapshot> rows) {
        return rows.stream()
                .filter(row -> nonNull(row.getExternalSize()))
                .filter(row -> row.getExternalSize().compareTo(BigDecimal.ZERO) > 0)
                .collect(toList());
    }

    private <T> Map<String, List<T>> byInstrument(List<T> rows, Function<T, String> address) {
        return rows.stream()
                .filter(row -> nonNull(address.apply(row)))
                .collect(groupingBy(address));
    }
}
