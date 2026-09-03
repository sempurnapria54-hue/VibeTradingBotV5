package com.example.tradingbot.domain.command.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingbot.domain.command.risk.DealRiskNumbersWriter;
import com.example.tradingbot.domain.command.resolve.ProtectionHistoryLeg;
import com.example.tradingbot.domain.command.resolve.OrderExternalStatusResolver;
import com.example.tradingbot.domain.command.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.safety.HoldRung;
import com.example.tradingbot.domain.safety.HoldScope;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.Constants;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Связывает цикл добычи материализованной защиты с его домом
 * (docs/models/mapping/Order.md §«OKX: цикл добычи материализованной
 * защиты», исходы — docs/lifecycles/Order.md).
 *
 * <p>Несущее для этого теста — <b>что цикл вообще запускается и чем он
 * ограничен</b>: до этого захода поверхности у него не было, резолвер
 * получал пустые факты, и живая материализованная защита читалась как
 * непредъявленная. Три границы проверяются поимённо: живой родитель цикла
 * не запускает; ветвь потери покрытия историю не опрашивает; пустой разбор
 * терминала не ставит.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshOrderProtectionCycleTest {

    private static final String INST_ID = "BTC-USDT-SWAP";
    private static final String PROTECTION_ID = "prot-1";
    private static final Long ORDER_ID = 10L;

    @Mock
    private OrderDataService orderDataService;

    @Mock
    private DealActionStateDataService dealActionStateDataService;

    @Mock
    private IntegrationService integrationService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderExternalStatusResolver orderStatusResolver;

    @Spy
    private AttachedAlgoOrderStateResolver attachedStateResolver = new AttachedAlgoOrderStateResolver();

    @Mock
    private DealRiskNumbersWriter riskNumbersWriter;

    @InjectMocks
    private RefreshOrderExecutor executor;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(ORDER_ID);
        order.setInternalId("ord-1");
        order.setDealTrancheId(1L);
        order.setStatus(Order.Status.COMPLETED);
        order.setAccumulatedFillSize(BigDecimal.ONE);
        order.setAttachedAlgoOrders(List.of(protection()));
        when(orderDataService.getRequiredById(ORDER_ID)).thenReturn(order);
        when(integrationService.getOrder(anyString(), any(), any())).thenReturn(snapshot());
        when(orderStatusResolver.resolve(any()))
                .thenReturn(StatusResolveResult.of(Order.Status.COMPLETED, null));
        when(integrationService.getPendingMaterializedProtections(anyString())).thenReturn(List.of());
        when(integrationService.getMaterializedProtectionHistory(anyString(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("У живого родителя цикл добычи не запускается: защита ещё в его теле")
    void liveParentDoesNotRunSearchCycle() {
        order.setStatus(Order.Status.ACTIVE);
        when(orderStatusResolver.resolve(any()))
                .thenReturn(StatusResolveResult.of(Order.Status.ACTIVE, null));

        executor.execute(command(), null, context(coveredTranche()));

        verify(integrationService, never()).getPendingMaterializedProtections(anyString());
        verify(integrationService, never()).getMaterializedProtectionHistory(anyString(), any());
    }

    @Test
    @DisplayName("Предъявленная живая запись делает защиту активной, историю не опрашивая")
    void liveRecordMakesProtectionActive() {
        when(integrationService.getPendingMaterializedProtections(INST_ID))
                .thenReturn(List.of(record(PROTECTION_ID)));

        executor.execute(command(), null, context(coveredTranche()));

        assertEquals(AttachedAlgoOrder.Status.ACTIVE, order.getAttachedAlgoOrders().getFirst().getStatus());
        verify(integrationService, never()).getMaterializedProtectionHistory(anyString(), any());
    }

    @Test
    @DisplayName("Живой непокрытый риск транша терминализует защиту потерянной, историю не опрашивая")
    void uncoveredExposureTerminatesWithoutHistory() {
        executor.execute(command(), null, context(bareTranche()));

        AttachedAlgoOrder attached = order.getAttachedAlgoOrders().getFirst();
        assertEquals(AttachedAlgoOrder.Status.ERROR, attached.getStatus());
        assertEquals(AttachedAlgoOrder.CloseReason.PROTECTION_LOST, attached.getCloseReason());
        verify(integrationService, never()).getMaterializedProtectionHistory(anyString(), any());
    }

    @Test
    @DisplayName("Нога разбора кодирует исход: запись в state=effective даёт сработавшую защиту")
    void historyLegCodesOutcome() {
        when(integrationService.getMaterializedProtectionHistory(INST_ID, ProtectionHistoryLeg.EFFECTIVE))
                .thenReturn(List.of(record(PROTECTION_ID)));

        executor.execute(command(), null, context(coveredTranche()));

        AttachedAlgoOrder attached = order.getAttachedAlgoOrders().getFirst();
        assertEquals(AttachedAlgoOrder.Status.COMPLETED, attached.getStatus());
        assertEquals(AttachedAlgoOrder.CloseReason.TRIGGERED, attached.getCloseReason());
        verify(integrationService, never())
                .getMaterializedProtectionHistory(INST_ID, ProtectionHistoryLeg.CANCELED);
    }

    @Test
    @DisplayName("Пустой разбор терминала не ставит: опрошены все три ноги, состояние прежнее")
    void emptyAnalysisLeavesStateUntouched() {
        ServiceCommandExecutionResult result = executor.execute(command(), null, context(coveredTranche()));

        AttachedAlgoOrder attached = order.getAttachedAlgoOrders().getFirst();
        assertEquals(AttachedAlgoOrder.Status.PENDING, attached.getStatus());
        assertNull(attached.getCloseReason());
        verify(integrationService).getMaterializedProtectionHistory(INST_ID, ProtectionHistoryLeg.EFFECTIVE);
        verify(integrationService).getMaterializedProtectionHistory(INST_ID, ProtectionHistoryLeg.CANCELED);
        verify(integrationService).getMaterializedProtectionHistory(INST_ID, ProtectionHistoryLeg.ORDER_FAILED);
        // Отсутствие факта ЗАТРЕБУЕТ мягкую ступень: риск покрыт, рвать нечем.
        HoldSignal requested = result.getHoldSignal();
        assertEquals(HoldScope.INSTRUMENT, requested.getScope());
        assertEquals(HoldRung.SOFT, requested.getRung());
        assertEquals(Constants.Hold.INSTRUMENT_PROTECTION_FATE_UNKNOWN, requested.getCode());
    }

    @Test
    @DisplayName("Найденный факт ступени не требует: она адресует именно ОТСУТСТВИЕ факта")
    void foundFactRaisesNoRung() {
        when(integrationService.getMaterializedProtectionHistory(INST_ID, ProtectionHistoryLeg.CANCELED))
                .thenReturn(List.of(record(PROTECTION_ID)));

        ServiceCommandExecutionResult result = executor.execute(command(), null, context(coveredTranche()));

        assertNull(result.getHoldSignal());
    }

    @Test
    @DisplayName("Совпадение ищется по клиентскому идентификатору: чужая запись защиту не предъявляет")
    void foreignRecordIsNotAMatch() {
        when(integrationService.getPendingMaterializedProtections(INST_ID))
                .thenReturn(List.of(record("someone-else")));

        executor.execute(command(), null, context(coveredTranche()));

        assertEquals(AttachedAlgoOrder.Status.PENDING, order.getAttachedAlgoOrders().getFirst().getStatus());
        // Чужая запись предъявлением не считается — разбор истории идёт полностью.
        verify(integrationService, times(3)).getMaterializedProtectionHistory(eq(INST_ID), any());
    }

    private ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ORDER)
                .dealId(1L)
                .payload(new RefreshOrderCommandPayload(ORDER_ID))
                .build();
    }

    private OrderExternalSnapshot snapshot() {
        return OrderExternalSnapshot.builder()
                .internalId("ord-1")
                .externalStatus("filled")
                .accumulatedFillSize(BigDecimal.ONE)
                .build();
    }

    private AttachedAlgoOrder protection() {
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setInternalId(PROTECTION_ID);
        attached.setStatus(AttachedAlgoOrder.Status.PENDING);
        attached.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
        attached.setSize(BigDecimal.ONE);
        return attached;
    }

    private AttachedAlgoOrderExternalSnapshot record(String internalId) {
        return AttachedAlgoOrderExternalSnapshot.builder()
                .internalId(internalId)
                .externalId("algo-77")
                .externalStatus("live")
                .size(BigDecimal.ONE)
                .build();
    }

    private DealContext context(DealTranche tranche) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setTranches(List.of(tranche));
        Instrument instrument = new Instrument();
        instrument.setId(5L);
        instrument.setExternalId(INST_ID);
        return DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .build();
    }

    /** Транш с живой экспозицией и без отдельной защиты: ветвь потери покрытия. */
    private DealTranche bareTranche() {
        return tranche();
    }

    /** Транш с живой экспозицией и отдельной защитой: ветвь разбора истории. */
    private DealTranche coveredTranche() {
        AlgoOrder standalone = new AlgoOrder();
        standalone.setStatus(AlgoOrder.Status.ACTIVE);
        standalone.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        standalone.setSize(BigDecimal.ONE);
        return tranche(standalone);
    }

    private DealTranche tranche(AlgoOrder... standalone) {
        DealTranche tranche = new DealTranche();
        tranche.setId(1L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEpisodeSeq(1);
        tranche.setEntryFilled(BigDecimal.ONE);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of(standalone));
        return tranche;
    }
}
