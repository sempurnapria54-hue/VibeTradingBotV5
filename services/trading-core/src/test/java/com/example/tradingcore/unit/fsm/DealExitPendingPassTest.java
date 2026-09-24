package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingError;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingRung;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeObserving;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeWithCommands;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.silentCascade;
import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.closedPosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.decimal;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import com.example.tradingcore.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheEdge;
import com.example.tradingcore.domain.fsm.deal.DealExitPendingHandler;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Координированный выход — группа {@code U10} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealExitPendingHandler.md §«Рабочая логика»).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code EXIT_PENDING}, два
 * нетерминальных транша; живой эпизод есть, его риск живой; граф полон;
 * живых входных ног нет; живых агрегатных строк исполнения нет; каскад и
 * исполнитель звеньев подменены, звенья добычи отдают свои команды.
 *
 * <p><b>Порядок выхода называется тремя ступенями:</b> при живом риске
 * позиции — закрытие нетто-экспозиции и его добыча; пока транши не
 * терминальны — ничего; затем добыча фактов закрытия и финализация. Прежде
 * финализация затребовалась бы на любом проходе без закрытия — и её бюджет
 * тратился бы, пока транши ещё снимаются.
 *
 * <p>Калькулятор итога — сервис-коллаборатор, и подменён: его предмет —
 * своя спека, а здесь спрашивается только, чем проход отвечает на его исход.
 */
class DealExitPendingPassTest {

    private final TrancheCascade cascade = mock(TrancheCascade.class);

    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);

    private final DealResultCalculator resultCalculator = mock(DealResultCalculator.class);

    private final DealExitPendingHandler handler = new DealExitPendingHandler(cascade,
            new DealTransitionGate(new DealTerminalGate()), systemActionExecutor, resultCalculator);

    DealExitPendingPassTest() {
        when(cascade.run(any())).thenReturn(silentCascade());
        when(systemActionExecutor.next(any(), any(), any())).thenReturn(Optional.empty());
        givenFetch(ServiceCommandType.REFRESH_POSITION_COMMAND);
        givenFetch(ServiceCommandType.REFRESH_BILLS_COMMAND);
        when(resultCalculator.flowsAwaitFetch(any())).thenReturn(Boolean.FALSE);
    }

    @Test
    @DisplayName("U10.1 — базовая сборка при молчащем каскаде: добыча позиции, затем её закрытие")
    void u10_1_theBaseStateEmitsTheNetCloseAfterItsFetch() {
        DealContext context = baseContext();

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND,
                ServiceCommandType.CLOSE_POSITION_COMMAND);
        ClosePositionCommandPayload payload =
                (ClosePositionCommandPayload) transition.getCommands().get(1).getPayload();
        assertThat(payload.getRequestedCloseReason()).isEqualTo(Position.CloseReason.CLOSED_BY_STRATEGY);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
    }

    @Test
    @DisplayName("U10.2 — у транша живая входная нога: ни закрытия, ни финализации")
    void u10_2_aLiveEntryLegHoldsTheNetCloseAndTheFinalization() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        givenFinalizationCommand();

        assertIdle(handler.handle(context));
    }

    @Test
    @DisplayName("U10.3 — граф предъявлен не целиком: то же")
    void u10_3_anIncompleteGraphHoldsTheNetCloseAndTheFinalization() {
        DealContext context = contextBuilder(baseDeal()).graphComplete(Boolean.FALSE).build();
        givenFinalizationCommand();

        assertIdle(handler.handle(context));
    }

    @Test
    @DisplayName("U10.4 — живого эпизода нет, транши не терминальны: финализация не затребуется")
    void u10_4_aDealWithoutALiveEpisodeWaitsForItsTranches() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().clear();
        givenFinalizationCommand();

        assertIdle(handler.handle(contextBuilder(collapsing).build()));
    }

    @Test
    @DisplayName("U10.5 — эпизод без живого риска, транши не терминальны: то же")
    void u10_5_aClosedEpisodeWaitsForItsTranches() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().clear();
        collapsing.getPositions().add(closedPosition("2"));
        givenFinalizationCommand();

        assertIdle(handler.handle(contextBuilder(collapsing).build()));
    }

    @Test
    @DisplayName("U10.6 — живая строка агрегатного исполнения: закрытие ведёт исполнитель действия")
    void u10_6_aLiveDealLevelExecutionRowHoldsTheNetClose() {
        DealContext context = contextBuilder(baseDeal())
                .actionStates(List.of(dealLevelRow()))
                .build();
        givenFinalizationCommand();

        assertIdle(handler.handle(context));
    }

    @Test
    @DisplayName("U10.7 — транши терминальны, сделка не входила: терминал ставит звено")
    void u10_7_aQuietCollapseRequestsTheExitFinalization() {
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(settledDeal()).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U10.8 — каскад выдал команды при живом риске позиции: закрытие их не ждёт и едет первым")
    void u10_8_theNetCloseDoesNotWaitForTheCascade() {
        DealContext context = baseContext();
        when(cascade.run(any())).thenReturn(cascadeWithCommands(ServiceCommandType.CANCEL_ORDER_COMMAND));

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND,
                ServiceCommandType.CLOSE_POSITION_COMMAND, ServiceCommandType.CANCEL_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U10.9 — каскад просит ошибочную тропу: рёбра траншей приложены, ступень донесена")
    void u10_9_anErrorRequestCarriesTheEdgesAndTheRung() {
        DealContext context = baseContext();
        List<TrancheEdge> edges = List.of(new TrancheEdge(context.getDeal().getTranches().getFirst(),
                DealTranche.Status.CLOSED, DealTranche.CloseReason.STRATEGY_EXIT));
        HoldSignal rung = HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
        when(cascade.run(any())).thenReturn(cascadeAskingError(edges, rung));
        when(systemActionExecutor.next(eq(SystemActionType.FINALIZE_DEAL_ERROR_ACTION), any(), isNull()))
                .thenReturn(Optional.of(command(ServiceCommandType.MARK_DEAL_ERROR_COMMAND)));

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
        assertThat(transition.getTrancheEdges()).isEqualTo(edges);
        assertThat(transition.getHoldSignal()).isEqualTo(rung);
    }

    @Test
    @DisplayName("U10.10 — живых эпизодов два: каскад не запускался, рёбер траншей нет")
    void u10_10_twoLiveEpisodesTakeTheErrorPathBeforeTheCascade() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().add(livePosition("2"));
        when(systemActionExecutor.next(eq(SystemActionType.FINALIZE_DEAL_ERROR_ACTION), any(), isNull()))
                .thenReturn(Optional.of(command(ServiceCommandType.MARK_DEAL_ERROR_COMMAND)));

        DealTransition transition = handler.handle(contextBuilder(collapsing).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
        assertThat(transition.getTrancheEdges()).isEmpty();
        verify(cascade, never()).run(any());
    }

    @Test
    @DisplayName("U10.11 — чужой живой риск: входной проверкой этого обработчика он не является")
    void u10_11_anUnattributedLiveOrderDoesNotBlockTheCollapse() {
        DealContext context = baseContext();
        context.getDeal().getOrders().add(liveEntryLeg(90L, null));

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).contains(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U10.12 — сделка без закреплённой детали: сворачиванию деталь не нужна")
    void u10_12_aRecoveredDealCollapsesTheSameWay() {
        Deal collapsing = baseDeal();
        collapsing.setEntryReason(Deal.EntryReason.RECOVERY);
        DealContext context = contextBuilder(collapsing).strategyDetail(null).build();

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).contains(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U10.13 — каскад просит ступень, работы больше нет: ступень едет с финализацией")
    void u10_13_aRungRequestTravelsWithTheFinalization() {
        HoldSignal rung = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
        when(cascade.run(any())).thenReturn(cascadeAskingRung(rung));
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(settledDeal()).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND);
        assertThat(transition.getHoldSignal()).isEqualTo(rung);
    }

    @Test
    @DisplayName("U10.14 — намерение закрытия стоит, позиция добывается этим проходом: закрытие едет за добычей")
    void u10_14_aStandingCloseIntentIsRepeatedBehindAFreshObservation() {
        DealContext context = baseContext();
        context.getDeal().livePosition().setCloseReason(Position.CloseReason.CLOSED_BY_STRATEGY);

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition))
                .as("позиция, пережившая принятое закрытие, закрывается снова; плоскую исполнитель "
                        + "закрытия пропустит по наблюдению той же добычи")
                .containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND,
                        ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U10.20 — намерение закрытия стоит, звено добычи ждёт отката: ни добычи, ни повтора")
    void u10_20_aStandingCloseIntentWithoutAnObservationIsNotRepeated() {
        DealContext context = baseContext();
        context.getDeal().livePosition().setCloseReason(Position.CloseReason.CLOSED_BY_STRATEGY);
        when(systemActionExecutor.next(eq(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION), any(), isNull(),
                eq(ServiceCommandType.REFRESH_POSITION_COMMAND), any()))
                .thenReturn(Optional.empty());

        DealTransition transition = handler.handle(context);

        assertThat(transition.getCommands())
                .as("без наблюдения этого прохода «отправлено, ещё не наблюдено» от «наблюдено, "
                        + "осталась» не отличить")
                .isEmpty();
    }

    @Test
    @DisplayName("U10.21 — намерения нет, звено добычи ждёт отката: первое закрытие едет и без добычи")
    void u10_21_theFirstCloseDoesNotWaitForAnObservation() {
        DealContext context = baseContext();
        when(systemActionExecutor.next(eq(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION), any(), isNull(),
                eq(ServiceCommandType.REFRESH_POSITION_COMMAND), any()))
                .thenReturn(Optional.empty());

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U10.15 — каскад только наблюдает, транши не терминальны, риска нет: добыча занимает проход")
    void u10_15_aCascadeObservationHoldsTheFinalization() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().clear();
        when(cascade.run(any())).thenReturn(cascadeObserving(ServiceCommandType.REFRESH_ORDER_COMMAND));
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(collapsing).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_ORDER_COMMAND);
    }

    @Test
    @DisplayName("U10.16 — транши терминальны, эпизод ждёт записи закрытия: добыча позиции, финализации нет")
    void u10_16_aMissingCloseRecordIsFetchedBeforeTheFinalization() {
        Deal entered = enteredSettledDeal();
        entered.getPositions().add(closedPosition("2"));
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(entered).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U10.17 — записи закрытия добыты, движения ждут добычи: звено движений, финализации нет")
    void u10_17_theBillsLinkIsEmittedBeforeTheFinalization() {
        Deal entered = enteredSettledDeal();
        entered.getPositions().add(recordedPosition());
        when(resultCalculator.flowsAwaitFetch(any())).thenReturn(Boolean.TRUE);
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(entered).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_BILLS_COMMAND);
    }

    @Test
    @DisplayName("U10.18 — факты закрытия добыты: финализация выхода")
    void u10_18_harvestedFactsLeadToTheFinalization() {
        Deal entered = enteredSettledDeal();
        entered.getPositions().add(recordedPosition());
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(entered).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND);
    }

    @Test
    @DisplayName("U10.19 — работа каскада при живой входной ноге: закрытия нет, едет только каскад")
    void u10_19_aLiveEntryLegKeepsTheNetCloseOutOfTheCascadePass() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        when(cascade.run(any())).thenReturn(cascadeWithCommands(ServiceCommandType.CANCEL_ORDER_COMMAND));

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CANCEL_ORDER_COMMAND);
    }

    // --- сборка ------------------------------------------------------------

    /** Ни закрытия, ни финализации: проход ждёт траншей. */
    private void assertIdle(DealTransition transition) {
        assertThat(transition.getCommands()).isEmpty();
        assertThat(transition.movesStatus()).isFalse();
    }

    private void givenFinalizationCommand() {
        when(systemActionExecutor.next(eq(SystemActionType.FINALIZE_DEAL_EXIT_ACTION), any(), isNull()))
                .thenReturn(Optional.of(command(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND)));
    }

    /** Звено добычи, названное явно, отдаёт свою команду. */
    private void givenFetch(ServiceCommandType link) {
        when(systemActionExecutor.next(eq(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION), any(), isNull(),
                eq(link), any()))
                .thenReturn(Optional.of(command(link)));
    }

    /** Живая строка исполнения УРОВНЯ СДЕЛКИ: транша у неё нет. */
    private DealActionState dealLevelRow() {
        DealActionState state = new DealActionState();
        state.setId(80L);
        state.setActionKind(ActionKind.STRATEGY);
        state.setStrategyActionId(5L);
        state.setStatus(DealActionStateStatus.SUBMITTED);
        return state;
    }

    private DealContext baseContext() {
        return contextBuilder(baseDeal()).build();
    }

    private Deal baseDeal() {
        Deal collapsing = deal(Deal.Status.EXIT_PENDING,
                exposed(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "1"),
                exposed(tranche(SECOND_TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "1"));
        collapsing.getPositions().add(livePosition("2"));
        return collapsing;
    }

    /** Транши терминальны, сделка не входила, эпизодов нет. */
    private Deal settledDeal() {
        return deal(Deal.Status.EXIT_PENDING,
                tranche(TRANCHE_ID, DealTranche.Status.CLOSED),
                tranche(SECOND_TRANCHE_ID, DealTranche.Status.CLOSED));
    }

    /** Транши терминальны, вход исполнялся: позиция по сделке наблюдалась. */
    private Deal enteredSettledDeal() {
        return deal(Deal.Status.EXIT_PENDING,
                exposed(tranche(TRANCHE_ID, DealTranche.Status.CLOSED), "1"),
                tranche(SECOND_TRANCHE_ID, DealTranche.Status.CLOSED));
    }

    /** Закрытый эпизод с добытой записью закрытия. */
    private Position recordedPosition() {
        Position recorded = closedPosition("2");
        recorded.setExternalRealizedProfit(decimal("3"));
        return recorded;
    }

    private List<ServiceCommandType> commandTypes(DealTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
