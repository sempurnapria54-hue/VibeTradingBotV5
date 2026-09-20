package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.DECLARATION_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.declaration;
import static com.example.tradingcore.unit.fsm.FsmFixture.detail;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.DealTrancheStateMachine;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheTransitionGate;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Машина транша: выбор обработчика и судьба отвергнутого ребра — группа
 * {@code U15} документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealTrancheStateMachine.md).
 *
 * <p><b>Базовая сборка.</b> Машина собрана настоящим гейтом; обработчик
 * статуса кейса подменяется заглушкой там, где предмет группы — сама
 * машина.
 *
 * <p><b>Отвергнутое ребро команд прохода не отменяет:</b> снимается ровно
 * половина выхода — статус и его причина, — а команды, просьбы к сделке и
 * затребованная ступень остаются.
 */
class TrancheStateMachinePassTest {

    private static final HoldSignal RUNG =
            HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);

    private final TrancheTransitionGate gate = spy(new TrancheTransitionGate());

    @Test
    @DisplayName("U15.1 — транш в каждом из шести активных статусов: по каждому выбран свой обработчик")
    void u15_1_eachActiveStatusPicksItsOwnHandler() {
        List<DealTrancheHandler> handlers = new ArrayList<>();
        for (DealTranche.Status status : DealTranche.Status.values()) {
            if (DealTranche.Status.CLOSED.equals(status)) {
                continue;
            }
            handlers.add(handler(status, TrancheTransition.command(commandOf(status))));
        }
        DealTrancheStateMachine machine = new DealTrancheStateMachine(handlers, gate);

        assertThat(handlers).hasSize(6);
        for (DealTrancheHandler handler : handlers) {
            DealTranche subject = tranche(TRANCHE_ID, handler.handledStatus());
            DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, subject)).build();

            TrancheTransition transition = machine.run(context, subject);

            assertThat(transition.getCommands()).singleElement()
                    .extracting(ServiceCommand::getDealId)
                    .isEqualTo(dealIdOf(handler.handledStatus()));
        }
    }

    @Test
    @DisplayName("U15.2 — транш CLOSED: обработчика нет, переход пустой, запись уровня отладки")
    void u15_2_theTerminalTrancheHasNoHandler() {
        DealTranche closed = tranche(TRANCHE_ID, DealTranche.Status.CLOSED);
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, closed)).build();
        DealTrancheStateMachine machine = machineOf(DealTranche.Status.MANAGING, TrancheTransition.stay());

        TrancheTransition transition;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTrancheStateMachine.class)) {
            transition = machine.run(context, closed);
            messages = capture.messages();
        }

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
        assertThat(transition.getShutdownRequested()).isNull();
        assertThat(transition.getHoldSignal()).isNull();
        assertThat(messages).containsExactly(
                "No handler for tranche status trancheId=" + TRANCHE_ID + " status=CLOSED");
    }

    @Test
    @DisplayName("U15.3 — переход без ребра: возвращён дословно, гейт не спрашивается")
    void u15_3_aTransitionWithoutAnEdgeSkipsTheGate() {
        TrancheTransition proposed =
                TrancheTransition.command(command(ServiceCommandType.CREATE_ORDER_COMMAND));

        TrancheTransition transition = runManaging(proposed, Deal.Status.ACTIVE);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
        verify(gate, never()).transitionAllowed(any(), any(), any());
    }

    @Test
    @DisplayName("U15.4 — необъявленное ребро и команда: команда сохранена, ребро и причина сняты")
    void u15_4_anUndeclaredEdgeLosesItsStatusAndReasonOnly() {
        TrancheTransition withReason = TrancheTransition.close(DealTranche.CloseReason.STRATEGY_EXIT)
                .withCommand(command(ServiceCommandType.CREATE_ORDER_COMMAND));

        TrancheTransition refused;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTrancheStateMachine.class,
                TrancheTransitionGate.class)) {
            refused = runManaging(withReason, Deal.Status.ACTIVE);
            messages = capture.messages();
        }

        assertThat(commandTypes(refused)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(refused.movesStatus()).isFalse();
        assertThat(refused.getCloseReason()).isNull();
        assertThat(messages).contains(
                "Tranche edge is not declared trancheId=" + TRANCHE_ID + " from=MANAGING to=CLOSED",
                "Tranche transition refused by matrix trancheId=" + TRANCHE_ID + " from=MANAGING to=CLOSED");

        TrancheTransition withRequests = TrancheTransition.escalate(RUNG)
                .withStatus(DealTranche.Status.CLOSED)
                .withCommand(command(ServiceCommandType.CREATE_ORDER_COMMAND));

        TrancheTransition kept = runManaging(withRequests, Deal.Status.ACTIVE);

        assertThat(kept.getDealErrorRequested()).isTrue();
        assertThat(kept.getHoldSignal()).isEqualTo(RUNG);
        assertThat(kept.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U15.5 — терминал при невыполненном контракте: тот же исход")
    void u15_5_aTerminalWithAnUnmetContractLosesItsStatusOnly() {
        DealTranche exiting = exposed(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "1");
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, exiting)).build();
        TrancheTransition proposed = TrancheTransition.close(DealTranche.CloseReason.STRATEGY_EXIT)
                .withCommand(command(ServiceCommandType.CREATE_ORDER_COMMAND));

        TrancheTransition transition =
                machineOf(DealTranche.Status.EXIT_PENDING, proposed).run(context, exiting);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U15.6 — вход под сворачиванием сделки: ребро снято с собственной записью")
    void u15_6_anEntryUnderCollapseIsRefusedWithItsOwnRecord() {
        TrancheTransition proposed = TrancheTransition.moveTo(DealTranche.Status.ENTRY_SUBMITTED);

        TrancheTransition transition;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTrancheStateMachine.class,
                TrancheTransitionGate.class)) {
            transition = runManaging(proposed, Deal.Status.EXIT_PENDING);
            messages = capture.messages();
        }

        assertThat(transition.movesStatus()).isFalse();
        assertThat(messages).contains("Tranche takes new risk under deal collapse trancheId=" + TRANCHE_ID
                + " to=ENTRY_SUBMITTED");
    }

    @Test
    @DisplayName("U15.7 — переоткрытие при невыполненных условиях: записей ровно одна — отказ машины")
    void u15_7_aRefusedReopenLogsOnlyTheMachineRecord() {
        DealTranche managed = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, managed))
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE)))
                .build();
        TrancheTransition proposed = TrancheTransition.moveTo(DealTranche.Status.ENTRY_SUBMITTED);

        TrancheTransition transition;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTrancheStateMachine.class,
                TrancheTransitionGate.class)) {
            transition = machineOf(DealTranche.Status.MANAGING, proposed).run(context, managed);
            messages = capture.messages();
        }

        assertThat(transition.movesStatus()).isFalse();
        assertThat(messages).containsExactly("Tranche transition refused by matrix trancheId=" + TRANCHE_ID
                + " from=MANAGING to=ENTRY_SUBMITTED");
    }

    @Test
    @DisplayName("U15.8 — любой исход: статуса и причины на модели транша машина не пишет")
    void u15_8_theMachineNeverWritesTheStatusOnTheModel() {
        DealTranche exiting = tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING);
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, exiting)).build();

        machineOf(DealTranche.Status.EXIT_PENDING,
                TrancheTransition.close(DealTranche.CloseReason.STRATEGY_EXIT)).run(context, exiting);

        assertThat(exiting.getStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
        assertThat(exiting.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U15.9 — просьба увести сделку ошибочной тропой доезжает неизменной")
    void u15_9_theDealErrorRequestTravelsUnchanged() {
        TrancheTransition transition = runManaging(TrancheTransition.escalate(), Deal.Status.ACTIVE);

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(List.of(TrancheTransition.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .toList())
                .doesNotContain(Deal.Status.class);
    }

    // --- сборка ------------------------------------------------------------

    private TrancheTransition runManaging(TrancheTransition proposed, Deal.Status dealStatus) {
        DealTranche managed = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
        DealContext context = contextBuilder(deal(dealStatus, managed)).build();
        return machineOf(DealTranche.Status.MANAGING, proposed).run(context, managed);
    }

    private DealTrancheStateMachine machineOf(DealTranche.Status status, TrancheTransition proposed) {
        return new DealTrancheStateMachine(List.of(handler(status, proposed)), gate);
    }

    private DealTrancheHandler handler(DealTranche.Status status, TrancheTransition proposed) {
        return new DealTrancheHandler() {

            @Override
            public DealTranche.Status handledStatus() {
                return status;
            }

            @Override
            public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
                return proposed;
            }
        };
    }

    /** Команда, чей адресат сделки кодирует статус обработчика: метка выбора. */
    private ServiceCommand commandOf(DealTranche.Status status) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.CREATE_ORDER_COMMAND)
                .dealId(dealIdOf(status))
                .build();
    }

    private Long dealIdOf(DealTranche.Status status) {
        return (long) status.ordinal();
    }

    private List<ServiceCommandType> commandTypes(TrancheTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
