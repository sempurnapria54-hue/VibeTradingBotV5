package com.example.tradingbot.domain.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.service.ControlledExchangeException;
import com.example.tradingbot.integration.service.ExchangeIntegrationException;
import com.example.tradingbot.integration.service.IntegrationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает сборку среза с гейтом полноты прохода
 * (docs/components/AnomalyJob.md §«Гейт полноты среза»).
 *
 * <p>Несущее для этого теста — <b>что усечение меряется у выданной
 * страницы, а не у склейки</b>. Счёт-широкий срез algo складывается из
 * вызова на семью, у каждой свой потолок; мера по сумме объявляла бы
 * проход неполным при полностью добытом срезе — и через три тика поднимала
 * бы мягкую биржевую ступень, снимаемую ВРУЧНУЮ, при исправной бирже.
 *
 * <p><b>Второе несущее — что нулевая позиция живым риском не считается.</b>
 * Источник отдаёт строку с {@code pos = 0} по закрытой позиции. Без
 * нормализации `A2` снёс бы биржу за закрытую позицию, а `A9` молчал бы
 * ровно в своей популяции — «позиции нет, а заявки есть».
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyScanReaderTest {

    private static final String INST = "BTC-USDT-SWAP";

    @Mock
    private IntegrationService integrationService;

    private AnomalyScanReader reader;

    @BeforeEach
    void setUp() {
        reader = new AnomalyScanReader(integrationService);
        when(integrationService.getPositions()).thenReturn(List.of());
        when(integrationService.getAllPendingOrders()).thenReturn(List.of());
        when(integrationService.getAllPendingAlgoOrders()).thenReturn(List.of());
    }

    @Test
    @DisplayName("Склейка семей выше потолка страницы проход НЕПОЛНЫМ не делает")
    void concatenatedFamiliesDoNotMarkPassIncomplete() {
        when(integrationService.getAllPendingAlgoOrders()).thenReturn(algoRows(120));

        AnomalyScan scan = reader.read();

        assertEquals(Boolean.TRUE, scan.getComplete());
        assertEquals(120, scan.algoOrdersOf(INST).size());
    }

    @Test
    @DisplayName("Усечение страницы — отказ стороны источника, и он делает проход неполным")
    void truncatedPageFromSourceMarksPassIncomplete() {
        when(integrationService.getAllPendingAlgoOrders())
                .thenThrow(new ExchangeIntegrationException("Счёт-широкий срез усечён потолком страницы"));

        AnomalyScan scan = reader.read();

        assertEquals(Boolean.FALSE, scan.getComplete());
        assertTrue(scan.algoOrdersOf(INST).isEmpty());
    }

    @Test
    @DisplayName("Нулевая позиция из среза выбывает: закрытая позиция живым риском не считается")
    void zeroSizedPositionIsNotLive() {
        when(integrationService.getPositions()).thenReturn(List.of(
                position(BigDecimal.ZERO), position(BigDecimal.valueOf(2))));

        AnomalyScan scan = reader.read();

        assertEquals(1, scan.positionsOf(INST).size());
        assertEquals(BigDecimal.valueOf(2), scan.positionsOf(INST).getFirst().getExternalSize());
    }

    @Test
    @DisplayName("Пустой размер живым риском тоже не считается")
    void nullSizedPositionIsNotLive() {
        when(integrationService.getPositions()).thenReturn(List.of(position(null)));

        AnomalyScan scan = reader.read();

        assertTrue(scan.positionsOf(INST).isEmpty());
    }

    @Test
    @DisplayName("Контроль: живая позиция в срезе остаётся, и проход полон")
    void livePositionSurvivesNormalization() {
        when(integrationService.getPositions()).thenReturn(List.of(position(BigDecimal.ONE)));

        AnomalyScan scan = reader.read();

        assertEquals(1, scan.positionsOf(INST).size());
        assertEquals(Boolean.TRUE, scan.getComplete());
    }

    @Test
    @DisplayName("Контролируемое исключение наверх ПРОХОДИТ: оно поднимает ступень 2 само")
    void controlledExceptionIsNotSwallowed() {
        when(integrationService.getPositions())
                .thenThrow(new ControlledExchangeException("инвариант источника нарушен"));

        assertThrows(ControlledExchangeException.class, () -> reader.read());
    }

    @Test
    @DisplayName("Отказ вызова проход неполным делает, а строки прочих срезов сохраняет")
    void failedCallMarksPassIncompleteAndKeepsOtherSlices() {
        when(integrationService.getAllPendingOrders())
                .thenThrow(new ExchangeIntegrationException("таймаут"));
        when(integrationService.getPositions()).thenReturn(List.of(position(BigDecimal.ONE)));

        AnomalyScan scan = reader.read();

        assertEquals(Boolean.FALSE, scan.getComplete());
        assertEquals(1, scan.positionsOf(INST).size());
    }

    private List<AlgoOrderExternalSnapshot> algoRows(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> AlgoOrderExternalSnapshot.builder()
                        .externalInstrumentId(INST)
                        .internalId("algo-" + index)
                        .build())
                .toList();
    }

    private PositionExternalSnapshot position(BigDecimal size) {
        return PositionExternalSnapshot.builder()
                .externalId("pos-" + size)
                .externalInstrumentId(INST)
                .externalSize(size)
                .build();
    }
}
