package com.example.tradingbot.domain.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import com.example.tradingbot.util.Constants;
import java.util.List;
import java.util.Map;
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
 * Связывает учётные детекторы с их домом (docs/components/AnomalyJob.md
 * §«Что ищет», строки A6/A8/A9).
 *
 * <p>Несущее для этого теста — <b>что штатное состояние аномалией не
 * считается</b>. У `A9` операнд БД обязателен: наша отдыхающая входная
 * заявка позиции ещё не имеет по построению, и без операнда детектор
 * запрещал бы входы на каждом нормальном входе. У `A8` направление именно
 * «локально терминальна, на бирже жива»: обратное — штатный факт
 * исполнения либо отмены.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountingDetectorsTest {

    private static final String INST = "BTC-USDT-SWAP";
    private static final Long INSTRUMENT_ID = 7L;

    @Mock
    private DealDataService dealDataService;

    @Mock
    private OrderDataService orderDataService;

    @Mock
    private AlgoOrderDataService algoOrderDataService;

    @Mock
    private AnomalyReaction reaction;

    @InjectMocks
    private AccountingDetectors detectors;

    @Test
    @DisplayName("A9: заявки без позиции и без живой сделки — мягкая ступень инструмента")
    void orphanOrdersRaiseInstrumentSoftRung() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.FALSE);

        detectors.detect(scanWithOrders(), exchange(Exchange.Status.ACTIVE), instrument());

        AnomalyFinding finding = captured();
        assertEquals(Constants.Hold.INSTRUMENT_ORPHAN_ORDERS, finding.getCode());
        assertEquals(HoldScope.INSTRUMENT, finding.getScope());
        assertEquals(HoldRung.SOFT, finding.getRung());
        assertEquals(Integer.valueOf(2), finding.getHysteresisTicks());
    }

    @Test
    @DisplayName("A9 МОЛЧИТ, когда заявку объясняет живая сделка: это штатный отдыхающий вход")
    void restingEntryOrderOfLiveDealIsNotOrphan() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);

        detectors.detect(scanWithOrders(), exchange(Exchange.Status.ACTIVE), instrument());

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("A6: жёсткая ступень биржи стоит, а на бирже живут сущности — журнальная строка")
    void standingHardRungWithLiveEntitiesIsReported() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);

        detectors.detect(scanWithOrders(), exchange(Exchange.Status.TRADE_BLOCKED), instrument());

        AnomalyFinding finding = captured();
        assertEquals(Constants.Hold.SAFETY_RUNG_NOT_ENFORCED, finding.getCode());
        assertEquals(Boolean.TRUE, finding.getJournalOnly());
    }

    @Test
    @DisplayName("A8: наша строка терминальна, а сущность на бирже жива — журнальная строка с предметом")
    void terminalLocallyButAliveOnExchange() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);
        String clientId = ClientIdGenerator.generateExchangeFacing();
        when(orderDataService.findByInternalId(clientId)).thenReturn(terminalOrder());

        detectors.detect(scanWithOwnOrder(clientId), exchange(Exchange.Status.ACTIVE), instrument());

        AnomalyFinding finding = captured();
        assertEquals(Constants.Hold.LOCAL_TERMINAL_ALIVE_ON_EXCHANGE, finding.getCode());
        assertEquals(clientId, finding.getSubjectExternalId());
        assertEquals(Boolean.TRUE, finding.getJournalOnly());
    }

    @Test
    @DisplayName("A8 МОЛЧИТ на живой нашей заявке: расхождения нет")
    void liveOrderIsNotAnomaly() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);
        String clientId = ClientIdGenerator.generateExchangeFacing();
        when(orderDataService.findByInternalId(clientId)).thenReturn(liveOrder());

        detectors.detect(scanWithOwnOrder(clientId), exchange(Exchange.Status.ACTIVE), instrument());

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("A8 не путает «мы закрыли» с «мы не заводили»: строки нет — не его предмет")
    void unknownClientIdIsNotTerminalLocally() {
        when(dealDataService.existsActiveByInstrumentId(anyLong())).thenReturn(Boolean.TRUE);
        String clientId = ClientIdGenerator.generateExchangeFacing();
        when(orderDataService.findByInternalId(clientId)).thenReturn(null);
        when(algoOrderDataService.findByInternalId(clientId)).thenReturn(null);

        detectors.detect(scanWithOwnOrder(clientId), exchange(Exchange.Status.ACTIVE), instrument());

        verify(reaction, never()).apply(any(), any());
    }

    private AnomalyFinding captured() {
        ArgumentCaptor<AnomalyFinding> captor = ArgumentCaptor.forClass(AnomalyFinding.class);
        verify(reaction).apply(captor.capture(), any());
        return captor.getValue();
    }

    private AnomalyScan scanWithOrders() {
        return AnomalyScan.builder()
                .positions(Map.of())
                .orders(Map.of(INST, List.of(OrderExternalSnapshot.builder()
                        .externalInstrumentId(INST).internalId("foreign").build())))
                .algoOrders(Map.of())
                .complete(true)
                .build();
    }

    private AnomalyScan scanWithOwnOrder(String clientId) {
        return AnomalyScan.builder()
                .positions(Map.of(INST, List.of(PositionExternalSnapshot.builder()
                        .externalInstrumentId(INST).build())))
                .orders(Map.of(INST, List.of(OrderExternalSnapshot.builder()
                        .externalInstrumentId(INST).internalId(clientId).build())))
                .algoOrders(Map.<String, List<AlgoOrderExternalSnapshot>>of())
                .complete(true)
                .build();
    }

    private Order terminalOrder() {
        Order order = new Order();
        order.setStatus(Order.Status.CANCELED);
        return order;
    }

    private Order liveOrder() {
        Order order = new Order();
        order.setStatus(Order.Status.ACTIVE);
        return order;
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId(INST);
        instrument.setStatus(Instrument.Status.ACTIVE);
        return instrument;
    }

    private Exchange exchange(Exchange.Status status) {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        exchange.setStatus(status);
        return exchange;
    }
}
