package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Эмиссия звеньев системного действия: из чего выводится стадия и когда
 * надобности больше нет (docs/components/SystemActionExecutor.md).
 *
 * <p><b>Стадия обязана выводиться из durable-факта, а не из памяти
 * прохода.</b> Каждый случай ниже — состояние, на котором вывод «по
 * счётчику шагов» дал бы другое звено: сделка с уже посчитанным числом,
 * сделка без входа, транш, переживший свой эпизод, строка, ждущая отката.
 */
class SystemActionEmissionTest {

    private final DealActionStateDataService dataService = mock(DealActionStateDataService.class);

    private SystemActionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SystemActionExecutor(dataService);
        when(dataService.save(any())).thenAnswer(invocation -> {
            DealActionState state = invocation.getArgument(0);
            if (isNull(state.getId())) {
                state.setId(99L);
            }
            return state;
        });
    }

    @Test
    void theExitActionEmitsTheComputationLinkWhileTheNumberIsNotWritten() {
        Deal deal = enteredDeal();

        Optional<ServiceCommand> command = executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION,
                context(deal), null);

        assertThat(command).isPresent();
        assertThat(command.get().getType()).isEqualTo(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND);
    }

    @Test
    void theExitActionEmitsTheTerminalLinkOnceTheNumberStands() {
        Deal deal = enteredDeal();
        deal.setResultProfit(new BigDecimal("10"));

        Optional<ServiceCommand> command = executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION,
                context(deal), null);

        assertThat(command.orElseThrow().getType()).isEqualTo(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);
    }

    @Test
    void thePathWithoutEntryHasNoComputationLinkAtAll() {
        Deal deal = deal(Deal.Status.ACTIVE);

        Optional<ServiceCommand> command = executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION,
                context(deal), null);

        assertThat(command.orElseThrow().getType()).isEqualTo(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);
    }

    @Test
    void aTerminalDealHasNoNeedLeft() {
        Deal deal = deal(Deal.Status.CLOSED);

        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, context(deal), null)).isEmpty();
        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, context(deal), null)).isEmpty();
    }

    @Test
    void theErrorActionCarriesTwoSeparateLinks() {
        Deal active = deal(Deal.Status.ACTIVE);
        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, context(active), null)
                .orElseThrow().getType()).isEqualTo(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);

        Deal failing = deal(Deal.Status.ERROR);
        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, context(failing), null)
                .orElseThrow().getType()).isEqualTo(ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);
    }

    @Test
    void theEntryConsolidationIsNeededOnlyWhileTheTrancheStandsInTheSubmittedEntry() {
        Deal deal = deal(Deal.Status.ACTIVE);
        DealTranche submitted = tranche(DealTranche.Status.ENTRY_SUBMITTED);
        DealTranche managing = tranche(DealTranche.Status.MANAGING);
        deal.setTranches(List.of(submitted, managing));
        DealContext dealContext = context(deal);

        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, dealContext, submitted)
                .orElseThrow().getType()).isEqualTo(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, dealContext, managing)).isEmpty();
    }

    @Test
    void theFetchCycleWalksLiveLegsThenAlgoOrdersAndFallsBackToThePosition() {
        Deal withLeg = deal(Deal.Status.ACTIVE);
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        tranche.setOrders(List.of(liveLeg(31L)));
        withLeg.setTranches(List.of(tranche));
        ServiceCommand byLeg = executor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION,
                context(withLeg), null).orElseThrow();
        assertThat(byLeg.getType()).isEqualTo(ServiceCommandType.REFRESH_ORDER_COMMAND);
        assertThat(((RefreshOrderCommandPayload) byLeg.getPayload()).getOrderId()).isEqualTo(31L);

        Deal withAlgo = deal(Deal.Status.ACTIVE);
        DealTranche algoTranche = tranche(DealTranche.Status.MANAGING);
        algoTranche.setAlgoOrders(List.of(liveAlgo()));
        withAlgo.setTranches(List.of(algoTranche));
        assertThat(executor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, context(withAlgo), null)
                .orElseThrow().getType()).isEqualTo(ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND);

        Deal quiet = deal(Deal.Status.ACTIVE);
        assertThat(executor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, context(quiet), null)
                .orElseThrow().getType()).isEqualTo(ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    void anExecutionAwaitingItsBackoffEmitsNothingAndIsRearmedOnceTheMomentCame() {
        Deal deal = enteredDeal();
        DealActionState waiting = systemState(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        waiting.setStatus(DealActionStateStatus.RETRY_PENDING);
        waiting.setNextRetryAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        DealContext dealContext = context(deal, waiting);

        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, dealContext, null)).isEmpty();

        waiting.setNextRetryAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, dealContext, null)).isPresent();
        assertThat(waiting.getStatus()).isEqualTo(DealActionStateStatus.PLANNED);
    }

    @Test
    void aLiveExecutionIsReusedAndTheRowCreatedByThisPassIsRegisteredInTheContext() {
        Deal deal = enteredDeal();
        DealActionState live = systemState(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        DealContext reused = context(deal, live);

        assertThat(executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, reused, null)
                .orElseThrow().getDealActionStateId()).isEqualTo(live.getId());
        verify(dataService, never()).save(any());

        DealContext fresh = context(deal);
        executor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, fresh, null);
        assertThat(fresh.getActionStates()).hasSize(1);
    }

    @Test
    void theRevisionClosesExecutionsWhoseNeedIsGoneAndLeavesTheLiveOnes() {
        Deal deal = deal(Deal.Status.ACTIVE);
        DealTranche live = tranche(DealTranche.Status.MANAGING);
        deal.setTranches(List.of(live));
        DealActionState outlivedEpisode = systemState(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION);
        outlivedEpisode.setDealTrancheId(live.getId());
        outlivedEpisode.setTrancheEpisodeSeq(0);
        DealActionState aggregate = systemState(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);

        executor.reviseLiveExecutions(context(deal, outlivedEpisode, aggregate));

        assertThat(outlivedEpisode.getStatus()).isEqualTo(DealActionStateStatus.SKIPPED);
        assertThat(aggregate.getStatus()).isEqualTo(DealActionStateStatus.PLANNED);
    }

    @Test
    void aTerminalDealClosesEveryLiveSystemExecution() {
        Deal deal = deal(Deal.Status.EMERGENCY_CLOSED);
        DealActionState aggregate = systemState(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);

        executor.reviseLiveExecutions(context(deal, aggregate));

        assertThat(aggregate.getStatus()).isEqualTo(DealActionStateStatus.SKIPPED);
    }

    @Test
    void anExplicitLinkAnchorsTheExecutionWithoutRederivingTheNeed() {
        Deal deal = deal(Deal.Status.EXIT_PENDING);

        ServiceCommand command = executor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, context(deal),
                null, ServiceCommandType.REFRESH_BILLS_COMMAND, null).orElseThrow();

        assertThat(command.getType()).isEqualTo(ServiceCommandType.REFRESH_BILLS_COMMAND);
        assertThat(command.getDealActionStateId()).isNotNull();
    }

    // --- сборка состояния ------------------------------------------------

    private static Deal deal(Deal.Status status) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(status);
        deal.setTranches(List.of(tranche(DealTranche.Status.MANAGING)));
        deal.setPositions(List.of());
        return deal;
    }

    /** Вошедшая сделка: позиция наблюдалась восстановлением. */
    private static Deal enteredDeal() {
        Deal deal = deal(Deal.Status.EXIT_PENDING);
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        deal.setPositions(List.of(episode));
        return deal;
    }

    private static DealTranche tranche(DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setId(2L);
        tranche.setStatus(status);
        tranche.setEpisodeSeq(1);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of());
        return tranche;
    }

    private static Order liveLeg(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(Order.Status.ACTIVE);
        return order;
    }

    private static AlgoOrder liveAlgo() {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(41L);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        return algoOrder;
    }

    private static DealActionState systemState(SystemActionType type) {
        DealActionState state = new DealActionState();
        state.setId(7L);
        state.setDealId(1L);
        state.setActionKind(ActionKind.SYSTEM);
        state.setSystemActionType(type);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }

    private static DealContext context(Deal deal, DealActionState... states) {
        return DealContext.builder()
                .deal(deal)
                .actionStates(new ArrayList<>(List.of(states)))
                .graphComplete(true)
                .build();
    }
}
