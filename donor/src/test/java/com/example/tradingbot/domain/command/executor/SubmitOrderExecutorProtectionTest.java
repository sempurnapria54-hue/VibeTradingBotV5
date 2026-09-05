package com.example.tradingbot.domain.command.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * SUBMIT_ORDER и встроенная защита (Т5): защита уходит на биржу вместе с
 * родителем, поэтому успешный ACK пишет ей факт «родитель отправлен»
 * (CREATED → PENDING, docs/lifecycles/Order.md); реджект постановки
 * статуса защиты не двигает.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmitOrderExecutorProtectionTest {

    private static final Long ORDER_ID = 5L;
    private static final Long DEAL_ID = 3L;
    private static final OffsetDateTime ACK_TS = OffsetDateTime.of(2026, 9, 2, 17, 33, 13, 0, ZoneOffset.UTC);

    @Mock
    private OrderDataService orderDataService;

    @Mock
    private DealActionStateDataService dealActionStateDataService;

    @Mock
    private DealDataService dealDataService;

    @Mock
    private IntegrationService integrationService;

    @InjectMocks
    private SubmitOrderExecutor executor;

    private Order order;
    private AttachedAlgoOrder attached;

    @BeforeEach
    void setUp() {
        attached = new AttachedAlgoOrder();
        attached.setInternalId("att-1");
        attached.setStatus(AttachedAlgoOrder.Status.CREATED);
        attached.setStopLossTriggerPrice(new BigDecimal("2000"));
        order = new Order();
        order.setId(ORDER_ID);
        order.setInternalId("ord-1");
        order.setStatus(Order.Status.CREATED);
        order.setAttachedAlgoOrders(List.of(attached));
        when(orderDataService.getRequiredById(ORDER_ID)).thenReturn(order);
        when(orderDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void successfulAckMarksProtectionPending() {
        when(integrationService.placeOrder(any(), anyString()))
                .thenReturn(ExchangeAck.builder().success(true).externalId("ext-1").externalCreatedAt(ACK_TS).build());

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), context());

        assertThat(result.getSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING);
        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.PENDING);
        verify(orderDataService).save(order);
    }

    @Test
    void rejectedAckLeavesProtectionCreated() {
        when(integrationService.placeOrder(any(), anyString()))
                .thenReturn(ExchangeAck.builder().success(false).message("reject").build());

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), context());

        assertThat(result.getSuccess()).isFalse();
        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CREATED);
        verify(orderDataService, never()).save(any());
        verify(dealDataService, never()).applyBillsWindowBegin(any(), any());
    }

    @Test
    void entryAckWritesBillsWindowBegin() {
        when(integrationService.placeOrder(any(), anyString()))
                .thenReturn(ExchangeAck.builder().success(true).externalId("ext-1").externalCreatedAt(ACK_TS).build());

        executor.execute(command(), new DealActionState(), context());

        verify(dealDataService).applyBillsWindowBegin(DEAL_ID, ACK_TS);
    }

    @Test
    void reduceOnlyAckDoesNotWriteBillsWindowBegin() {
        order.setPositionReducingOnly(true);
        when(integrationService.placeOrder(any(), anyString()))
                .thenReturn(ExchangeAck.builder().success(true).externalId("ext-1").externalCreatedAt(ACK_TS).build());

        executor.execute(command(), new DealActionState(), context());

        verify(dealDataService, never()).applyBillsWindowBegin(any(), any());
    }

    @Test
    void ackWithoutExchangeTimeDoesNotFabricateWindowBegin() {
        when(integrationService.placeOrder(any(), anyString()))
                .thenReturn(ExchangeAck.builder().success(true).externalId("ext-1").build());

        executor.execute(command(), new DealActionState(), context());

        verify(dealDataService, never()).applyBillsWindowBegin(any(), any());
    }

    private ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.SUBMIT_ORDER_COMMAND)
                .dealId(DEAL_ID)
                .payload(new SubmitOrderCommandPayload(ORDER_ID))
                .build();
    }

    private DealContext context() {
        Instrument instrument = new Instrument();
        instrument.setExternalId("ETH-USDT-SWAP");
        return DealContext.builder().instrument(instrument).build();
    }
}
