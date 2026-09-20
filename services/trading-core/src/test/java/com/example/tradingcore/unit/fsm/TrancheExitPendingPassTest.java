package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.attachedProtection;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.filledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.filledReduceOnlyLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveReduceOnlyLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.protection;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static com.example.tradingcore.unit.fsm.FsmFixture.triggeredProtection;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.DealTrancheStateMachine;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheTransitionGate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Выход транша: порядок снятия и терминал — группа {@code U21} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/TrancheExitPendingHandler.md; инвариант порядка —
 * docs/rules/exit-teardown-order.md).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ACTIVE}; транш
 * {@code EXIT_PENDING} с ненулевой экспозицией, без живых ног и защит;
 * граф полон; рабочий блок подменён и молчит; диспозиция настоящая,
 * исполнитель звеньев подменён.
 *
 * <p><b>Клетка {@code U21.14} добрана этим заходом</b> — она закрывает
 * пробел {@code G3} документа: тропа «экспозиция ненулевая, рабочий блок
 * молчит» доходит до добычи фактов, и кейса на неё группа не имела.
 */
class TrancheExitPendingPassTest {

    private final TrancheHarness harness = new TrancheHarness();

    @Test
    @DisplayName("U21.1 — живая входная нога и живая защита: снимается НОГА, защита не трогается")
    void u21_1_theLiveEntryLegIsCancelledFirst() {
        DealTranche subject = exitingTranche();
        subject.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        subject.getAlgoOrders().add(protection(40L, TRANCHE_ID, "5"));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(cancelledOrderId(transition)).isEqualTo(30L);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U21.2 — живая входная нога при ненулевой экспозиции: та же команда")
    void u21_2_theExposureIsClosedAfterTheLeg() {
        DealTranche subject = exitingTranche();
        subject.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(cancelledOrderId(handle(contextOf(Deal.Status.ACTIVE, subject)))).isEqualTo(30L);
    }

    @Test
    @DisplayName("U21.3 — входных ног нет, сделка не сворачивается: исход рабочего блока")
    void u21_3_theTrancheExitsWithItsOwnReduceOnlyLeg() {
        harness.givenWork(TrancheTransition.command(command(ServiceCommandType.CREATE_ORDER_COMMAND)));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, exitingTranche()));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
    }

    @Test
    @DisplayName("U21.4 — входных ног нет, сделка сворачивается: своей ноги транш не выпускает")
    void u21_4_aCollapsingDealSuppressesTheOwnReduceOnlyLeg() {
        harness.givenWork(TrancheTransition.command(command(ServiceCommandType.CREATE_ORDER_COMMAND)));
        harness.givenSystemCommand(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION,
                ServiceCommandType.REFRESH_POSITION_COMMAND);

        TrancheTransition transition = handle(contextOf(Deal.Status.EXIT_PENDING, exitingTranche()));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND);
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U21.5 — экспозиция ноль, живая reduce-only нога: терминал её не гейтит")
    void u21_5_aLeftoverReduceOnlyLegIsCancelled() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING);
        subject.getOrders().add(liveReduceOnlyLeg(31L, TRANCHE_ID));

        assertThat(cancelledOrderId(handle(contextOf(Deal.Status.ACTIVE, subject)))).isEqualTo(31L);
    }

    @Test
    @DisplayName("U21.6 — экспозиция ноль, живая условная заявка: команда снятия условной заявки")
    void u21_6_aLeftoverConditionalOrderIsCancelled() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING);
        subject.getAlgoOrders().add(protection(40L, TRANCHE_ID, "5"));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND);
    }

    @Test
    @DisplayName("U21.7 — инициатор выхода назван стопом: терминал с причиной «стоп-лосс»")
    void u21_7_aTriggeredStopNamesTheCloseReason() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "5", "5");
        subject.getAlgoOrders().add(triggeredProtection(40L, TRANCHE_ID, AlgoOrder.ConditionType.STOP_LOSS));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.STOP_LOSS);
    }

    @Test
    @DisplayName("U21.8 — инициатора выхода нет вовсе: причина «закрыто извне»")
    void u21_8_anAbsentInitiatorGivesTheExternalCloseReason() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "5", "5");

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.EXTERNAL_CLOSE);
    }

    @Test
    @DisplayName("U21.9 — сделка сворачивается: собственный инициатор не спрашивается")
    void u21_9_aCollapsingDealOverridesTheOwnInitiator() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "5", "5");
        subject.getOrders().add(filledReduceOnlyLeg(31L, TRANCHE_ID, "5"));
        DealContext context = contextOf(Deal.Status.EXIT_PENDING, subject);
        context.getDeal().setCloseReason(Deal.CloseReason.RISK_CONTROL);

        TrancheTransition transition = handle(context);

        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.RISK_CONTROL);
    }

    @Test
    @DisplayName("U21.10 — транш всё ещё несёт живой риск: команда звена добычи фактов")
    void u21_10_aRiskBearingTrancheRequestsTheContextHarvest() {
        harness.givenSystemCommand(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION,
                ServiceCommandType.REFRESH_POSITION_COMMAND);
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "5", "5");
        Order parent = filledEntryLeg(30L, TRANCHE_ID, "5");
        parent.getAttachedAlgoOrders().add(attachedProtection(60L, "5"));
        subject.getOrders().add(parent);

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U21.11 — живых эпизодов два: ни снятия, ни терминала")
    void u21_11_twoLiveEpisodesEscalateBeforeTheTeardown() {
        DealContext context = contextOf(Deal.Status.ACTIVE, exitingTranche());
        context.getDeal().getPositions().add(livePosition("5"));
        context.getDeal().getPositions().add(livePosition("5"));

        TrancheTransition transition = handle(context);

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U21.12 — транш без объявления и без детали: обе пустоты проверку не заваливают")
    void u21_12_aRecoveredTrancheStillReachesItsTerminal() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "5", "5");
        subject.setStrategyTrancheId(null);
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, subject))
                .strategyDetail(null)
                .build();

        TrancheTransition transition = handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.EXTERNAL_CLOSE);
    }

    @Test
    @DisplayName("U21.13 — граф не целиком: обработчик ребро предлагает, машина его отвергает")
    void u21_13_theProposedTerminalIsRefusedByTheMatrixOnAnIncompleteGraph() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "5", "5");
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, subject))
                .graphComplete(Boolean.FALSE)
                .build();

        TrancheTransition proposed = harness.exitPending().handle(context, subject);
        TrancheTransition applied = machineOf(subject).run(context, subject);

        assertThat(proposed.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(applied.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U21.14 — экспозиция ненулевая, рабочий блок молчит: проход доходит до добычи фактов")
    void u21_14_aQuietWorkBlockWithLiveExposureRequestsTheContextHarvest() {
        harness.givenSystemCommand(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION,
                ServiceCommandType.REFRESH_POSITION_COMMAND);

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, exitingTranche()));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    // --- сборка ------------------------------------------------------------

    private TrancheTransition handle(DealContext context) {
        return harness.exitPending().handle(context, context.getDeal().getTranches().getFirst());
    }

    /** Машина с настоящим гейтом и обработчиком выхода. */
    private DealTrancheStateMachine machineOf(DealTranche subject) {
        DealTrancheHandler handler = harness.exitPending();
        assertThat(handler.handledStatus()).isEqualTo(subject.getStatus());
        return new DealTrancheStateMachine(List.of(handler), new TrancheTransitionGate());
    }

    /** Транш выхода с экспозицией 5, без живых ног и защит. */
    private DealTranche exitingTranche() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "5", "0");
        subject.getOrders().add(filledEntryLeg(30L, TRANCHE_ID, "5"));
        return subject;
    }

    private DealContext contextOf(Deal.Status status, DealTranche subject) {
        Deal owner = deal(status, subject);
        owner.getPositions().add(livePosition("5"));
        return contextBuilder(owner).build();
    }

    private Long cancelledOrderId(TrancheTransition transition) {
        ServiceCommand emitted = transition.getCommands().getFirst();
        assertThat(emitted.getType()).isEqualTo(ServiceCommandType.CANCEL_ORDER_COMMAND);
        return ((CancelOrderCommandPayload) emitted.getPayload()).getOrderId();
    }

    private List<ServiceCommandType> commandTypes(TrancheTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
