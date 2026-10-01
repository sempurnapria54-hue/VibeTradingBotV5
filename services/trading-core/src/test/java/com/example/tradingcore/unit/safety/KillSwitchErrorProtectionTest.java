package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_EXTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.position;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.KillSwitchProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.CancelAlgoOrderExecutor;
import com.example.tradingcore.domain.command.executor.CancelAttachedProtectionExecutor;
import com.example.tradingcore.domain.command.executor.CancelOrderExecutor;
import com.example.tradingcore.domain.command.executor.ClosePositionExecutor;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.safety.KillSwitchExecutor;
import com.example.tradingcore.domain.safety.PositionSliceReader;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

/**
 * Снятие риска с отдельной условной заявкой в {@code ERROR} — группа `U19`
 * документа `.claude/tests/cases/trading-core-safety.md` (дом —
 * docs/components/KillSwitchExecutor.md §Порядок, §Подтверждение;
 * множество и таблица наблюдений — docs/lifecycles/AlgoOrder.md §«Заявка в
 * {@code ERROR}: живость на площадке читается наблюдением»).
 *
 * <p><b>Базовая сборка:</b> исполнитель снятия риска с НАСТОЯЩИМ
 * исполнителем снятия отдельной защиты — его вызов площадки и есть
 * наблюдаемый протокол; хранилище условных заявок отвечает из графа сделки;
 * клиент площадки подтверждает приём любого снятия; диспетчер добычи
 * отвечает успехом и состояния не меняет; предел попыток — один. Сделка с
 * одним траншем без ног, эпизод закрыт; у транша — отдельная условная
 * заявка, помеченная ошибкой. Живость заявки собирается её полями — статусом
 * и наблюдением, — а не подменённым предикатом.
 */
class KillSwitchErrorProtectionTest {

    private static final Long DEAL_ID = 7L;
    private static final Long TRANCHE_ID = 61L;
    private static final Long ALGO_ORDER_ID = 41L;

    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final ServiceCommandExecutor commands = mock(ServiceCommandExecutor.class);
    private final AlgoOrderDataService algoOrders = mock(AlgoOrderDataService.class);
    private final KillSwitchProperties properties = new KillSwitchProperties();

    private KillSwitchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KillSwitchExecutor(exchange, commands, mock(DealContextService.class),
                mock(ExchangeAccountDataService.class), mock(InstrumentDataService.class), properties,
                mock(CancelOrderExecutor.class), mock(ClosePositionExecutor.class),
                new CancelAlgoOrderExecutor(algoOrders, mock(DealActionStateDataService.class), exchange),
                mock(CancelAttachedProtectionExecutor.class), new PositionSliceReader(exchange));
        properties.setMaxTeardownAttempts(1);
        when(commands.execute(any(), any())).thenReturn(ServiceCommandExecutionResult.ok());
        when(exchange.cancelAlgoOrder(anyString(), any(), anyString())).thenReturn(ack());
    }

    /**
     * Пометка ошибки живости не исключает: заявка снимается в очереди защит,
     * добыча подтверждения её берёт, а без наблюдённой нежилости снятие не
     * подтверждено.
     */
    @Test
    @DisplayName("U19.1 — позиция закрыта, заявка в ERROR без наблюдения: снята, добыта, снятие не подтверждено")
    void u19_1_anUnobservedErrorProtectionIsCancelledAndHoldsTheConfirmation() {
        AlgoOrder protection = errorProtection(null);
        DealContext dealContext = contextWith(protection, closedEpisode());

        ServiceCommandExecutionResult result = executor.execute(dealContext);

        verify(exchange).cancelAlgoOrder(eq(ACCOUNT_INTERNAL_ID), eq(protection), eq(INSTRUMENT_EXTERNAL_ID));
        verify(commands).execute(argThat(refreshOf(ALGO_ORDER_ID)), eq(dealContext));
        assertThat(result.getSuccess()).isFalse();
        assertThat(protection.getStatus())
                .as("проблемный терминал запечатан: отмена статуса не двигает")
                .isEqualTo(AlgoOrder.Status.ERROR);
        assertThat(protection.getCloseReason())
                .as("причина ошибки write-once: намерение снятия её не перетирает")
                .isEqualTo(AlgoOrder.CloseReason.UNKNOWN_EXTERNAL_STATUS);
    }

    @Test
    @DisplayName("U19.2 — заявка в ERROR, наблюдённая нежилой: снятия и добычи нет, снятие подтверждено")
    void u19_2_anErrorProtectionObservedNotLiveNeitherIsCancelledNorHolds() {
        AlgoOrder protection = errorProtection(Boolean.FALSE);
        DealContext dealContext = contextWith(protection, closedEpisode());

        ServiceCommandExecutionResult result = executor.execute(dealContext);

        verify(exchange, never()).cancelAlgoOrder(anyString(), any(), anyString());
        verify(commands, never()).execute(argThat(refreshOf(ALGO_ORDER_ID)), any());
        assertThat(result.getSuccess()).isTrue();
    }

    /** Подтверждает снятие наблюдённая нежилость, а не статус. */
    @Test
    @DisplayName("U19.3 — добыча подтверждения показала заявку нежилой: снятие подтверждено той же попыткой")
    void u19_3_theConfirmationFetchObservingNonLivenessConfirmsTheTeardown() {
        AlgoOrder protection = errorProtection(null);
        DealContext dealContext = contextWith(protection, closedEpisode());
        when(commands.execute(argThat(refreshOf(ALGO_ORDER_ID)), any())).thenAnswer(invocation -> {
            protection.setExternalLive(Boolean.FALSE);
            return ServiceCommandExecutionResult.ok();
        });

        ServiceCommandExecutionResult result = executor.execute(dealContext);

        verify(exchange).cancelAlgoOrder(eq(ACCOUNT_INTERNAL_ID), eq(protection), eq(INSTRUMENT_EXTERNAL_ID));
        assertThat(result.getSuccess()).isTrue();
    }

    /** Защита снимается последней: живая позиция не оголяется ни на мгновение. */
    @Test
    @DisplayName("U19.4 — эпизод жив, заявка в ERROR без наблюдения: пока экспозиция жива, заявка не снимается")
    void u19_4_anErrorProtectionIsNotCancelledWhileTheExposureIsLive() {
        AlgoOrder protection = errorProtection(null);
        DealContext dealContext = contextWith(protection,
                position(Position.Status.ACTIVE, BigDecimal.ONE, null));

        executor.execute(dealContext);

        verify(exchange, never()).cancelAlgoOrder(anyString(), any(), anyString());
    }

    private DealContext contextWith(AlgoOrder protection, Position episode) {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setStatus(DealTranche.Status.EXIT_PENDING);
        tranche.setOrders(new ArrayList<>());
        tranche.setAlgoOrders(new ArrayList<>(List.of(protection)));
        Deal deal = deal(DEAL_ID);
        deal.setStatus(Deal.Status.ERROR);
        deal.getTranches().add(tranche);
        deal.getPositions().add(episode);
        when(algoOrders.getRequiredById(ALGO_ORDER_ID)).thenReturn(protection);
        return pairContext(deal);
    }

    /** Отдельная защита, помеченная ошибкой, с названным наблюдением площадки. */
    private static AlgoOrder errorProtection(Boolean externalLive) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(ALGO_ORDER_ID);
        algoOrder.setDealId(DEAL_ID);
        algoOrder.setDealTrancheId(TRANCHE_ID);
        algoOrder.setStatus(AlgoOrder.Status.ERROR);
        algoOrder.setCloseReason(AlgoOrder.CloseReason.UNKNOWN_EXTERNAL_STATUS);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(BigDecimal.ONE);
        algoOrder.setExternalLive(externalLive);
        return algoOrder;
    }

    private static Position closedEpisode() {
        return position(Position.Status.CLOSED, BigDecimal.ZERO, null);
    }

    private static ArgumentMatcher<ServiceCommand> refreshOf(Long algoOrderId) {
        return command -> ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND.equals(command.getType())
                && Objects.equals(algoOrderId, ((RefreshAlgoOrderCommandPayload) command.getPayload())
                        .getAlgoOrderId());
    }

    private static ExchangeAck ack() {
        ExchangeAck exchangeAck = new ExchangeAck();
        exchangeAck.setSuccess(true);
        return exchangeAck;
    }
}
