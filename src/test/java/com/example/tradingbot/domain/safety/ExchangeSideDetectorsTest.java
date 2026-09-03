package com.example.tradingbot.domain.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.util.ClientIdGenerator;
import com.example.tradingbot.util.Constants;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает биржевые детекторы с их домом
 * (docs/components/AnomalyJob.md §«Что ищет», строки A2/A3/A7;
 * docs/rules/exchange-hold.md §«Ступень 2 — сворачивание» пп. 1 и 3).
 *
 * <p>Несущее для этого теста — <b>дискриминатор «наше против чужого»</b>.
 * У `A7` он стои́т на стороне БИРЖИ (маркер контура в клиентском
 * идентификаторе), а не на «в БД строки нет»: заявка, ушедшая до коммита
 * своей строки, переживает рестарт, и по построчному признаку детектор
 * снёс бы биржу за нашу собственную заявку.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeSideDetectorsTest {

    private static final String MANAGED = "BTC-USDT-SWAP";
    private static final String FOREIGN = "DOGE-USDT-SWAP";

    @Mock
    private AnomalyReaction reaction;

    @InjectMocks
    private ExchangeSideDetectors detectors;

    @Test
    @DisplayName("A2: живая сущность по инструменту вне контура — биржевая ступень 2")
    void liveRiskOutsideContourRaisesExchangeTeardown() {
        AnomalyScan scan = scan(Map.of(FOREIGN, List.of(position())), Map.of(), Map.of());

        detectors.detect(scan, exchange(), Set.of(MANAGED));

        assertEquals(Constants.Hold.EXCHANGE_FOREIGN_INSTRUMENT_RISK, captured().getCode());
        assertEquals(HoldScope.EXCHANGE, captured().getScope());
        assertEquals(HoldRung.HARD, captured().getRung());
    }

    @Test
    @DisplayName("A3: больше одной позиции на инструмент — биржевая ступень 2")
    void duplicatePositionRaisesExchangeTeardown() {
        AnomalyScan scan = scan(Map.of(MANAGED, List.of(position(), position())), Map.of(), Map.of());

        detectors.detect(scan, exchange(), Set.of(MANAGED));

        assertEquals(Constants.Hold.EXCHANGE_POSITION_MODE_VIOLATION, captured().getCode());
    }

    @Test
    @DisplayName("A3 не срабатывает на одной позиции: штатное состояние аномалией не считается")
    void singlePositionIsNotAnomaly() {
        AnomalyScan scan = scan(Map.of(MANAGED, List.of(position())), Map.of(), Map.of());

        detectors.detect(scan, exchange(), Set.of(MANAGED));

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("A7: заявка без маркера контура — биржевая ступень 2")
    void orderWithoutMarkerRaisesExchangeTeardown() {
        AnomalyScan scan = scan(Map.of(), Map.of(MANAGED, List.of(order("someone-elses-id"))), Map.of());

        detectors.detect(scan, exchange(), Set.of(MANAGED));

        assertEquals(Constants.Hold.EXCHANGE_FOREIGN_ORDER, captured().getCode());
    }

    @Test
    @DisplayName("A7 НЕ срабатывает на нашей заявке: маркер её опознаёт, строка в БД не нужна")
    void ourOwnOrderIsNotForeign() {
        AnomalyScan scan = scan(Map.of(),
                Map.of(MANAGED, List.of(order(ClientIdGenerator.generateExchangeFacing()))), Map.of());

        detectors.detect(scan, exchange(), Set.of(MANAGED));

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("A7 видит и algo-заявку без маркера")
    void foreignAlgoIsDetected() {
        AnomalyScan scan = scan(Map.of(), Map.of(), Map.of(MANAGED, List.of(algo(null))));

        detectors.detect(scan, exchange(), Set.of(MANAGED));

        assertEquals(Constants.Hold.EXCHANGE_FOREIGN_ORDER, captured().getCode());
    }

    @Test
    @DisplayName("Гистерезиса у биржевых признаков нет: гонка чтения их не производит")
    void exchangeSideFindingsCarryNoHysteresis() {
        AnomalyScan scan = scan(Map.of(FOREIGN, List.of(position())), Map.of(), Map.of());

        detectors.detect(scan, exchange(), Set.of(MANAGED));

        assertEquals(Integer.valueOf(1), captured().getHysteresisTicks());
    }

    private AnomalyFinding captured() {
        ArgumentCaptor<AnomalyFinding> captor = ArgumentCaptor.forClass(AnomalyFinding.class);
        verify(reaction).apply(captor.capture(), any());
        return captor.getValue();
    }

    private AnomalyScan scan(Map<String, List<PositionExternalSnapshot>> positions,
                             Map<String, List<OrderExternalSnapshot>> orders,
                             Map<String, List<AlgoOrderExternalSnapshot>> algos) {
        return AnomalyScan.builder()
                .positions(positions)
                .orders(orders)
                .algoOrders(algos)
                .complete(true)
                .build();
    }

    private PositionExternalSnapshot position() {
        return PositionExternalSnapshot.builder().externalInstrumentId(MANAGED).build();
    }

    private OrderExternalSnapshot order(String clientId) {
        return OrderExternalSnapshot.builder().externalInstrumentId(MANAGED).internalId(clientId).build();
    }

    private AlgoOrderExternalSnapshot algo(String clientId) {
        return AlgoOrderExternalSnapshot.builder().externalInstrumentId(MANAGED).internalId(clientId).build();
    }

    private Exchange exchange() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        return exchange;
    }
}
