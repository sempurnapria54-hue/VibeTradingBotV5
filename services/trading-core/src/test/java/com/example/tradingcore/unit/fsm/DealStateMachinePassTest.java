package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.DEAL_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
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
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealHandler;
import com.example.tradingcore.domain.fsm.DealStateMachine;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Машина сделки: выбор обработчика и судьба отвергнутого ребра — группа
 * {@code U6} документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/DealStateMachine.md).
 *
 * <p><b>Базовая сборка.</b> Машина собрана настоящим гейтом; обработчик
 * по статусу кейса подменяется заглушкой, возвращающей объявленный
 * переход, — предмет группы сама машина, а не логика обработчика.
 *
 * <p><b>Единица кейса — проход целиком:</b> ожидание называет обе
 * половины выхода. Кейс, называющий только ребро, зелен у машины,
 * потерявшей команды; кейс, называющий только команды, зелен у машины,
 * пропускающей необъявленное ребро.
 */
class DealStateMachinePassTest {

    private final DealTransitionGate gate = spy(new DealTransitionGate(new DealTerminalGate()));

    @Test
    @DisplayName("U6.1 — сделка ACTIVE: выбран обработчик активной сделки, его переход возвращён как есть")
    void u6_1_theActiveStatusPicksItsOwnHandler() {
        DealTransition transition = machineOfThree().run(context(Deal.Status.ACTIVE));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
    }

    @Test
    @DisplayName("U6.2 — сделка EXIT_PENDING: выбран обработчик координированного выхода")
    void u6_2_theExitPendingStatusPicksItsOwnHandler() {
        DealTransition transition = machineOfThree().run(context(Deal.Status.EXIT_PENDING));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U6.3 — сделка ERROR: выбран обработчик ошибочного состояния")
    void u6_3_theErrorStatusPicksItsOwnHandler() {
        DealTransition transition = machineOfThree().run(context(Deal.Status.ERROR));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    @DisplayName("U6.4 — сделка CLOSED: обработчика нет, переход пустой, запись уровня отладки")
    void u6_4_theCleanTerminalHasNoHandler() {
        assertEmptyPassWithDebugRecord(Deal.Status.CLOSED);
    }

    @Test
    @DisplayName("U6.5 — сделка EMERGENCY_CLOSED: то же пустое")
    void u6_5_theEmergencyTerminalHasNoHandler() {
        assertEmptyPassWithDebugRecord(Deal.Status.EMERGENCY_CLOSED);
    }

    @Test
    @DisplayName("U6.6 — переход без ребра: возвращён дословно, гейт не спрашивается")
    void u6_6_aTransitionWithoutAnEdgeSkipsTheGate() {
        DealTransition proposed = DealTransition.stay()
                .withCommand(command(ServiceCommandType.CREATE_ORDER_COMMAND))
                .withCommand(command(ServiceCommandType.SUBMIT_ORDER_COMMAND));

        DealTransition transition = machineOf(Deal.Status.ACTIVE, proposed).run(context(Deal.Status.ACTIVE));

        assertThat(commandTypes(transition)).containsExactly(
                ServiceCommandType.CREATE_ORDER_COMMAND, ServiceCommandType.SUBMIT_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        verify(gate, never()).transitionAllowed(any(), any());
    }

    @Test
    @DisplayName("U6.7 — необъявленное ребро и две команды: команды сохранены, ребро снято")
    void u6_7_anUndeclaredEdgeIsDroppedWhileTheCommandsSurvive() {
        HoldSignal rung = HoldSignal.instrument(Constants.Hold.INSTRUMENT_MARKET_DATA_EXPIRED);
        DealTransition proposed = DealTransition.moveTo(Deal.Status.EMERGENCY_CLOSED)
                .withCommand(command(ServiceCommandType.CREATE_ORDER_COMMAND))
                .withCommand(command(ServiceCommandType.SUBMIT_ORDER_COMMAND))
                .withRung(rung);

        DealTransition transition;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealStateMachine.class, DealTransitionGate.class)) {
            transition = machineOf(Deal.Status.ACTIVE, proposed).run(context(Deal.Status.ACTIVE));
            messages = capture.messages();
        }

        assertThat(commandTypes(transition)).containsExactly(
                ServiceCommandType.CREATE_ORDER_COMMAND, ServiceCommandType.SUBMIT_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getShutdownReason()).isNull();
        assertThat(transition.getCloseReason()).isNull();
        assertThat(transition.getHoldSignal()).isEqualTo(rung);
        assertThat(messages).contains(
                "Deal edge is not declared dealId=" + DEAL_ID + " from=ACTIVE to=EMERGENCY_CLOSED",
                "Deal transition refused by matrix dealId=" + DEAL_ID + " from=ACTIVE to=EMERGENCY_CLOSED");
    }

    @Test
    @DisplayName("U6.8 — объявленное ребро при невыполненном контракте: тот же исход")
    void u6_8_aDeclaredEdgeWithAnUnmetContractLosesOnlyItsStatus() {
        DealContext context = context(Deal.Status.ACTIVE);
        context.getDeal().setResultProfit(null);
        DealTransition proposed = DealTransition.moveTo(Deal.Status.CLOSED)
                .withCommand(command(ServiceCommandType.CREATE_ORDER_COMMAND));

        DealTransition transition = machineOf(Deal.Status.ACTIVE, proposed).run(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U6.9 — ребро в координированный выход с обеими причинами: гейт разрешил")
    void u6_9_anAllowedCollapseKeepsBothReasons() {
        DealTransition proposed = DealTransition.collapse(Deal.ShutdownReason.STRATEGY_DELETED,
                Deal.CloseReason.STRATEGY_EXIT);

        DealTransition transition = machineOf(Deal.Status.ACTIVE, proposed).run(context(Deal.Status.ACTIVE));

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getShutdownReason()).isEqualTo(Deal.ShutdownReason.STRATEGY_DELETED);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.STRATEGY_EXIT);
    }

    @Test
    @DisplayName("U6.10 — то же ребро отвергнуто: обе причины сняты вместе с ним")
    void u6_10_aRefusedCollapseDropsBothReasonsWithTheEdge() {
        DealTransition proposed = DealTransition.collapse(Deal.ShutdownReason.STRATEGY_DELETED,
                Deal.CloseReason.STRATEGY_EXIT);

        DealTransition transition = machineOf(Deal.Status.ERROR, proposed).run(context(Deal.Status.ERROR));

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getShutdownReason()).isNull();
        assertThat(transition.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U6.11 — любой исход: статуса на модели сделки машина не меняет")
    void u6_11_theMachineNeverWritesTheStatusOnTheModel() {
        DealContext allowed = context(Deal.Status.ACTIVE);
        machineOf(Deal.Status.ACTIVE, DealTransition.moveTo(Deal.Status.EXIT_PENDING)).run(allowed);

        DealContext refused = context(Deal.Status.ERROR);
        machineOf(Deal.Status.ERROR, DealTransition.moveTo(Deal.Status.CLOSED)).run(refused);

        assertThat(allowed.getDeal().getStatus()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(refused.getDeal().getStatus()).isEqualTo(Deal.Status.ERROR);
    }

    // --- сборка ------------------------------------------------------------

    private void assertEmptyPassWithDebugRecord(Deal.Status status) {
        DealTransition transition;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealStateMachine.class)) {
            transition = machineOfThree().run(context(status));
            messages = capture.messages();
        }

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
        assertThat(transition.getTrancheEdges()).isEmpty();
        assertThat(messages).containsExactly(
                "No handler for deal status dealId=" + DEAL_ID + " status=" + status);
    }

    /** Машина с тремя заглушками — по одной на нетерминальный статус. */
    private DealStateMachine machineOfThree() {
        return new DealStateMachine(List.of(
                handler(Deal.Status.ACTIVE, commandPass(ServiceCommandType.CREATE_ORDER_COMMAND)),
                handler(Deal.Status.EXIT_PENDING, commandPass(ServiceCommandType.CLOSE_POSITION_COMMAND)),
                handler(Deal.Status.ERROR, commandPass(ServiceCommandType.REFRESH_POSITION_COMMAND))),
                gate);
    }

    /** Машина с одной заглушкой на названный статус. */
    private DealStateMachine machineOf(Deal.Status status, DealTransition proposed) {
        return new DealStateMachine(List.of(handler(status, proposed)), gate);
    }

    private DealHandler handler(Deal.Status status, DealTransition proposed) {
        return new DealHandler() {

            @Override
            public Deal.Status handledStatus() {
                return status;
            }

            @Override
            public DealTransition handle(DealContext dealContext) {
                return proposed;
            }
        };
    }

    private DealTransition commandPass(ServiceCommandType type) {
        return DealTransition.commands(List.of(command(type)));
    }

    private ServiceCommand command(ServiceCommandType type) {
        return ServiceCommand.builder().type(type).dealId(DEAL_ID).build();
    }

    private List<ServiceCommandType> commandTypes(DealTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }

    /** Базовая сборка: терминальный транш, число и валюта результата стоя́т. */
    private DealContext context(Deal.Status status) {
        DealTranche closed = fills(tranche(TRANCHE_ID, DealTranche.Status.CLOSED), "5", "5");
        Deal deal = deal(status, closed);
        deal.setResultProfit(new BigDecimal("10"));
        deal.setResultProfitCurrency("USDT");
        return contextBuilder(deal).build();
    }
}
