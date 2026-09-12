package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.KillSwitchProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.safety.KillSwitchExecutor;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Аварийное снятие живого риска: порядок хода и то, чем подтверждается
 * снятие (docs/components/KillSwitchExecutor.md).
 *
 * <p><b>Порядок здесь — инвариант, а не удобство.</b> Две перестановки
 * ломают его молча: закрытие экспозиции раньше снятия живых ног (нога
 * доливается после закрытия и открывает позицию заново) и снятие защиты
 * раньше подтверждённого закрытия (позиция остаётся оголённой). Ни та ни
 * другая не видна по результату — обе дают «успех».
 *
 * <p>Состояние собирается настоящим графом, а не подменёнными предикатами
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»): живость ноги
 * вытекает из её статуса, живой риск — из статуса и размера эпизода.
 */
class KillSwitchTeardownTest {

    private static final String ACCOUNT = "ea-test-0001";
    private static final String INSTRUMENT = "ETH-USDT-SWAP";
    private static final Long PARENT_LEG_ID = 21L;

    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final ServiceCommandExecutor commands = mock(ServiceCommandExecutor.class);
    private final DealContextService contextService = mock(DealContextService.class);
    private final KillSwitchProperties properties = new KillSwitchProperties();

    private KillSwitchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KillSwitchExecutor(exchange, commands, contextService, properties);
        properties.setMaxTeardownAttempts(1);
        when(commands.execute(any(), any())).thenReturn(ServiceCommandExecutionResult.ok());
        when(exchange.cancelOrder(anyString(), any(), anyString())).thenReturn(ack());
        when(exchange.cancelAlgoOrder(anyString(), any(), anyString())).thenReturn(ack());
        when(exchange.cancelAttachedProtection(anyString(), any(), anyString())).thenReturn(ack());
        when(exchange.closePosition(anyString(), anyString(), any())).thenReturn(ack());
    }

    @Test
    void liveLegsAreCancelledBeforeTheExposureIsClosed() {
        Order leg = leg(11L, Order.Status.ACTIVE, List.of());
        DealContext dealContext = context(deal(tranche(leg, null), livePosition()));

        executor.execute(dealContext);

        InOrder order = inOrder(exchange);
        order.verify(exchange).cancelOrder(eq(ACCOUNT), eq(leg), eq(INSTRUMENT));
        order.verify(exchange).closePosition(eq(ACCOUNT), eq(INSTRUMENT), any());
    }

    @Test
    void protectionIsNotTouchedWhileTheExposureIsStillLive() {
        AttachedAlgoOrder attached = attached(31L, PARENT_LEG_ID, AttachedAlgoOrder.Status.ACTIVE);
        Order parent = leg(PARENT_LEG_ID, Order.Status.COMPLETED, List.of(attached));
        AlgoOrder standalone = algo(41L, AlgoOrder.Status.ACTIVE);
        DealContext dealContext = context(deal(tranche(parent, standalone), livePosition()));

        executor.execute(dealContext);

        verify(exchange, never()).cancelAlgoOrder(anyString(), any(), anyString());
        verify(exchange, never()).cancelAttachedProtection(anyString(), any(), anyString());
    }

    @Test
    void protectionOfBothFormsIsCancelledOnceTheCloseIsConfirmed() {
        AttachedAlgoOrder attached = attached(31L, PARENT_LEG_ID, AttachedAlgoOrder.Status.ACTIVE);
        Order parent = leg(PARENT_LEG_ID, Order.Status.COMPLETED, List.of(attached));
        AlgoOrder standalone = algo(41L, AlgoOrder.Status.ACTIVE);
        Deal deal = deal(tranche(parent, standalone), livePosition());
        DealContext dealContext = context(deal);
        onGraphReload(deal, () -> deal.setPositions(List.of(closedPosition())));

        executor.execute(dealContext);

        verify(exchange).cancelAlgoOrder(eq(ACCOUNT), eq(standalone), eq(INSTRUMENT));
        verify(exchange).cancelAttachedProtection(eq(ACCOUNT), eq(attached), eq(INSTRUMENT));
    }

    @Test
    void anAttachedProtectionOutlivingItsParentIsConfirmedThroughThatParent() {
        AttachedAlgoOrder attached = attached(31L, PARENT_LEG_ID, AttachedAlgoOrder.Status.ACTIVE);
        Order parent = leg(PARENT_LEG_ID, Order.Status.COMPLETED, List.of(attached));
        DealContext dealContext = context(deal(tranche(parent, null), closedPosition()));

        executor.execute(dealContext);

        verify(commands).execute(argThat(refreshOf(PARENT_LEG_ID)), eq(dealContext));
        verify(exchange).cancelAttachedProtection(eq(ACCOUNT), eq(attached), eq(INSTRUMENT));
    }

    @Test
    void flatIsConfirmedByFetchedFactsAndNotByTheExchangeAnswer() {
        DealContext dealContext = context(deal(tranche(null, null), closedPosition()));

        ServiceCommandExecutionResult result = executor.execute(dealContext);

        assertThat(result.getSuccess()).isTrue();
        verify(commands, times(2)).execute(argThat(refreshPosition()), eq(dealContext));
    }

    @Test
    void factsThatCouldNotBeFetchedAreNotAConfirmation() {
        DealContext dealContext = context(deal(tranche(null, null), closedPosition()));
        when(commands.execute(any(), any())).thenThrow(new IllegalStateException("connector is down"));

        ServiceCommandExecutionResult result = executor.execute(dealContext);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
    }

    @Test
    void anUnconfirmedTeardownIsRepeatedWithinTheLimitAndThenFails() {
        properties.setMaxTeardownAttempts(2);
        DealContext dealContext = context(deal(tranche(null, null), livePosition()));

        ServiceCommandExecutionResult result = executor.execute(dealContext);

        verify(exchange, times(2)).closePosition(eq(ACCOUNT), eq(INSTRUMENT), any());
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
    }

    @Test
    void aBoundaryFailureOnOneSubjectDoesNotAbortTheTeardown() {
        Order leg = leg(11L, Order.Status.ACTIVE, List.of());
        DealContext dealContext = context(deal(tranche(leg, null), livePosition()));
        when(exchange.cancelOrder(anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("connector is down"));

        executor.execute(dealContext);

        verify(exchange).closePosition(eq(ACCOUNT), eq(INSTRUMENT), any());
    }

    private void onGraphReload(Deal deal, Runnable mutation) {
        doAnswer(invocation -> {
            mutation.run();
            return null;
        }).when(contextService).reloadRuntimeGraph(deal);
    }

    private static org.mockito.ArgumentMatcher<ServiceCommand> refreshOf(Long orderId) {
        return command -> ServiceCommandType.REFRESH_ORDER_COMMAND.equals(command.getType())
                && Objects.equals(orderId, ((RefreshOrderCommandPayload) command.getPayload()).getOrderId());
    }

    private static org.mockito.ArgumentMatcher<ServiceCommand> refreshPosition() {
        return command -> ServiceCommandType.REFRESH_POSITION_COMMAND.equals(command.getType());
    }

    private static ExchangeAck ack() {
        ExchangeAck exchangeAck = new ExchangeAck();
        exchangeAck.setSuccess(true);
        return exchangeAck;
    }

    private static Order leg(Long id, Order.Status status, List<AttachedAlgoOrder> protections) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setPositionReducingOnly(false);
        order.setAttachedAlgoOrders(protections);
        return order;
    }

    private static AlgoOrder algo(Long id, AlgoOrder.Status status) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(id);
        algoOrder.setStatus(status);
        return algoOrder;
    }

    private static AttachedAlgoOrder attached(Long id, Long orderId, AttachedAlgoOrder.Status status) {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setId(id);
        protection.setOrderId(orderId);
        protection.setStatus(status);
        return protection;
    }

    private static Position livePosition() {
        Position position = new Position();
        position.setId(51L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(BigDecimal.ONE);
        return position;
    }

    private static Position closedPosition() {
        Position position = new Position();
        position.setId(51L);
        position.setStatus(Position.Status.CLOSED);
        position.setExternalSize(BigDecimal.ZERO);
        return position;
    }

    private static DealTranche tranche(Order leg, AlgoOrder standalone) {
        DealTranche tranche = new DealTranche();
        tranche.setId(61L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setOrders(isNull(leg) ? List.of() : List.of(leg));
        tranche.setAlgoOrders(isNull(standalone) ? List.of() : List.of(standalone));
        return tranche;
    }

    private static Deal deal(DealTranche tranche, Position position) {
        Deal deal = new Deal();
        deal.setId(77L);
        deal.setStatus(Deal.Status.ERROR);
        deal.setTranches(List.of(tranche));
        deal.setPositions(List.of(position));
        return deal;
    }

    private static DealContext context(Deal deal) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(4L);
        account.setInternalId(ACCOUNT);
        Instrument instrument = new Instrument();
        instrument.setId(9L);
        instrument.setExternalId(INSTRUMENT);
        instrument.setExternalSettlementCurrency("USDT");
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .build();
    }
}
