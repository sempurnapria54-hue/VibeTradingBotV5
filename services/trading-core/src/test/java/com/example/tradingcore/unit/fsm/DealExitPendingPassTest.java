package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingError;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeAskingRung;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.cascadeWithCommands;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.DealActiveHarness.silentCascade;
import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.closedPosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
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
 * исполнитель звеньев подменены.
 *
 * <p><b>Обе половины исхода называются вместе:</b> отсутствие закрытия
 * нетто-экспозиции проход не останавливает — он доходит до затребования
 * финализации выхода тем же ходом.
 */
class DealExitPendingPassTest {

    private final TrancheCascade cascade = mock(TrancheCascade.class);

    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);

    private final DealExitPendingHandler handler = new DealExitPendingHandler(cascade,
            new DealTransitionGate(new DealTerminalGate()), systemActionExecutor);

    DealExitPendingPassTest() {
        when(cascade.run(any())).thenReturn(silentCascade());
        when(systemActionExecutor.next(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("U10.1 — базовая сборка при молчащем каскаде: одна команда закрытия позиции")
    void u10_1_theBaseStateEmitsTheNetClose() {
        DealContext context = baseContext();

        DealTransition transition = handler.handle(context);

        assertThat(transition.getCommands()).singleElement()
                .satisfies(emitted -> {
                    assertThat(emitted.getType()).isEqualTo(ServiceCommandType.CLOSE_POSITION_COMMAND);
                    ClosePositionCommandPayload payload = (ClosePositionCommandPayload) emitted.getPayload();
                    assertThat(payload.getRequestedCloseReason())
                            .isEqualTo(Position.CloseReason.CLOSED_BY_STRATEGY);
                });
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
    }

    @Test
    @DisplayName("U10.2 — у транша живая входная нога: закрытия нет, затребование финализации есть")
    void u10_2_aLiveEntryLegHoldsTheNetCloseButNotTheFinalization() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        givenFinalizationCommand();

        assertOnlyFinalization(handler.handle(context));
    }

    @Test
    @DisplayName("U10.3 — граф предъявлен не целиком: то же обеими половинами")
    void u10_3_anIncompleteGraphHoldsTheNetCloseButNotTheFinalization() {
        DealContext context = contextBuilder(baseDeal()).graphComplete(Boolean.FALSE).build();
        givenFinalizationCommand();

        assertOnlyFinalization(handler.handle(context));
    }

    @Test
    @DisplayName("U10.4 — живого эпизода нет вовсе: закрывать нечего, проход идёт к финализации")
    void u10_4_aDealWithoutALiveEpisodeGoesStraightToTheFinalization() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().clear();
        givenFinalizationCommand();

        assertOnlyFinalization(handler.handle(contextBuilder(collapsing).build()));
    }

    @Test
    @DisplayName("U10.5 — эпизод есть, живого риска не несёт: то же")
    void u10_5_aClosedEpisodeGoesStraightToTheFinalization() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().clear();
        collapsing.getPositions().add(closedPosition("2"));
        givenFinalizationCommand();

        assertOnlyFinalization(handler.handle(contextBuilder(collapsing).build()));
    }

    @Test
    @DisplayName("U10.6 — живая строка агрегатного исполнения: закрытие ведёт исполнитель действия")
    void u10_6_aLiveDealLevelExecutionRowHoldsTheNetClose() {
        DealContext context = contextBuilder(baseDeal())
                .actionStates(List.of(dealLevelRow()))
                .build();
        givenFinalizationCommand();

        assertOnlyFinalization(handler.handle(context));
    }

    @Test
    @DisplayName("U10.7 — живого риска нет и живых строк нет: терминал ставит звено")
    void u10_7_aQuietCollapseRequestsTheExitFinalization() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().clear();
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(collapsing).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U10.8 — каскад выдал команды: закрытие нетто-экспозиции на этом проходе не эмитится")
    void u10_8_theCascadeCommandsPreemptTheNetClose() {
        DealContext context = baseContext();
        when(cascade.run(any())).thenReturn(cascadeWithCommands(ServiceCommandType.CANCEL_ORDER_COMMAND));

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CANCEL_ORDER_COMMAND);
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

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U10.12 — сделка без закреплённой детали: сворачиванию деталь не нужна")
    void u10_12_aRecoveredDealCollapsesTheSameWay() {
        Deal collapsing = baseDeal();
        collapsing.setEntryReason(Deal.EntryReason.RECOVERY);
        DealContext context = contextBuilder(collapsing).strategyDetail(null).build();

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U10.13 — каскад просит ступень, работы больше нет: ступень едет с финализацией")
    void u10_13_aRungRequestTravelsWithTheFinalization() {
        Deal collapsing = baseDeal();
        collapsing.getPositions().clear();
        HoldSignal rung = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
        when(cascade.run(any())).thenReturn(cascadeAskingRung(rung));
        givenFinalizationCommand();

        DealTransition transition = handler.handle(contextBuilder(collapsing).build());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND);
        assertThat(transition.getHoldSignal()).isEqualTo(rung);
    }

    // --- сборка ------------------------------------------------------------

    /** Закрытия нет, затребование финализации есть — обе половины исхода. */
    private void assertOnlyFinalization(DealTransition transition) {
        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    private void givenFinalizationCommand() {
        when(systemActionExecutor.next(eq(SystemActionType.FINALIZE_DEAL_EXIT_ACTION), any(), isNull()))
                .thenReturn(Optional.of(command(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND)));
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

    private List<ServiceCommandType> commandTypes(DealTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
