package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.cancelledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.filledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.leg;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отправленный вход — группа {@code U18} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/TrancheEntrySubmittedHandler.md).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ACTIVE}; транш
 * {@code ENTRY_SUBMITTED} с живой входной ногой без наливов; чужого риска
 * нет; рабочий блок подменён и молчит; исполнитель звеньев подменён.
 *
 * <p><b>Ребро в подтверждённый вход обработчик не пишет:</b> он эмитит
 * команду консолидации, а само ребро ставит звено в одной транзакции со
 * своим завершением.
 */
class TrancheEntrySubmittedPassTest {

    private final TrancheHarness harness = new TrancheHarness();

    @Test
    @DisplayName("U18.1 — базовая сборка, рабочий блок молчит: добыча живой ноги вместе с позицией")
    void u18_1_theBaseStateObservesTheLegTogetherWithThePosition() {
        givenFetches();

        TrancheTransition transition = handle(baseContext());

        assertThat(transition.hasCommands()).isFalse();
        assertThat(observationTypes(transition)).containsExactly(ServiceCommandType.REFRESH_ORDER_COMMAND,
                ServiceCommandType.REFRESH_POSITION_COMMAND);
        RefreshOrderCommandPayload payload =
                (RefreshOrderCommandPayload) transition.getObservations().getFirst().getPayload();
        assertThat(payload.getOrderId()).isEqualTo(30L);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
    }

    @Test
    @DisplayName("U18.2 — входной ноги нет вовсе: состояние невозможное")
    void u18_2_anAbsentEntryLegEscalates() {
        DealContext context = contextOf(Deal.Status.ACTIVE,
                tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED));

        assertThat(handle(context).getDealErrorRequested()).isTrue();
    }

    @Test
    @DisplayName("U18.3 — чужой живой риск либо второй эпизод: та же просьба")
    void u18_3_aForeignLiveRiskEscalates() {
        DealContext context = baseContext();
        context.getDeal().getOrders().add(liveEntryLeg(90L, null));

        assertThat(handle(context).getDealErrorRequested()).isTrue();
    }

    @Test
    @DisplayName("U18.4 — сделка сворачивается при живой ноге: ребро в выход транша")
    void u18_4_aCollapsingDealSendsTheLiveLegToTheExit() {
        DealContext context = contextOf(Deal.Status.EXIT_PENDING, submittedTranche());

        assertThat(handle(context).getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U18.5 — сделка сворачивается, ноги уже нет: терминал с наследованной причиной")
    void u18_5_aCollapsingDealClosesTheTrancheWithTheInheritedReason() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED);
        subject.getOrders().add(cancelledEntryLeg(30L, TRANCHE_ID));
        DealContext context = contextOf(Deal.Status.EXIT_PENDING, subject);
        context.getDeal().setCloseReason(Deal.CloseReason.STOP_LOSS);

        TrancheTransition transition = handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.STOP_LOSS);
    }

    @Test
    @DisplayName("U18.6 — нога терминальна, наливов не было: причина «условие входа истекло»")
    void u18_6_aTerminalLegWithoutFillsClosesOnTheExpiredCondition() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED);
        subject.getOrders().add(cancelledEntryLeg(30L, TRANCHE_ID));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
    }

    @Test
    @DisplayName("U18.7 — нога терминальна, налив был: тропа истёкшего условия не применяется")
    void u18_7_aTerminalLegWithFillsSkipsTheExpiredConditionPath() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "5", "5");
        subject.getOrders().add(cancelledEntryLeg(30L, TRANCHE_ID));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(transition.getCloseReason()).isNotEqualTo(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    @Test
    @DisplayName("U18.8 — налив был, живого эпизода и живой ноги нет: позицию закрыли на бирже")
    void u18_8_aFilledLegWithoutALiveEpisodeGoesToTheExit() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "5", "5");
        subject.getOrders().add(filledEntryLeg(30L, TRANCHE_ID, "5"));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
        assertThat(transition.hasCommands()).isFalse();
    }

    @Test
    @DisplayName("U18.9 — нога налита целиком при живом эпизоде: команда звена консолидации")
    void u18_9_aConfirmedEntryRequestsTheConsolidationLink() {
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION,
                ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);

        TrancheTransition transition = handle(confirmedEntryContext());

        assertThat(commandTypes(transition))
                .containsExactly(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U18.10 — живая строка звена уже есть: переход пустой")
    void u18_10_aLiveConsolidationRowLeavesThePassEmpty() {
        TrancheTransition transition = handle(confirmedEntryContext());

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U18.11 — нога налита частично: консолидация не затребуется, налив добывается дальше")
    void u18_11_aPartiallyFilledLegFallsThroughToTheWorkBlock() {
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION,
                ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        givenFetches();
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "2", "0");
        subject.getOrders().add(leg(30L, TRANCHE_ID, Order.Status.PARTIALLY_COMPLETED, Boolean.FALSE, "2"));
        DealContext context = contextOf(Deal.Status.ACTIVE, subject);
        context.getDeal().getPositions().add(livePosition("2"));

        TrancheTransition transition = handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(observationTypes(transition)).containsExactly(ServiceCommandType.REFRESH_ORDER_COMMAND,
                ServiceCommandType.REFRESH_POSITION_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U18.12 — транш пришёл ребром переоткрытия: тропы различает состояние ноги")
    void u18_12_aReopenedTrancheIsJudgedByItsLegNotByItsEpisodeNumber() {
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION,
                ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "5", "5");
        subject.setEpisodeSeq(2);
        subject.getOrders().add(liveEntryLeg(31L, TRANCHE_ID));
        DealContext context = contextOf(Deal.Status.ACTIVE, subject);
        context.getDeal().getPositions().add(livePosition("1"));

        TrancheTransition transition = handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U18.13 — нога налита, эпизод живой, звено консолидации ждёт: добыча не нужна — переход пустой")
    void u18_13_aConfirmedEntryIsNotObservedAgain() {
        givenFetches();

        TrancheTransition transition = handle(confirmedEntryContext());

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.hasObservations()).isFalse();
    }

    @Test
    @DisplayName("U18.14 — рабочий блок заговорил: его исход, добычи нет")
    void u18_14_aSpeakingWorkBlockTakesThePass() {
        givenFetches();
        harness.givenWork(TrancheTransition.escalate());

        TrancheTransition transition = handle(baseContext());

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.hasObservations()).isFalse();
    }

    // --- сборка ------------------------------------------------------------

    private void givenFetches() {
        harness.givenFetch(ServiceCommandType.REFRESH_ORDER_COMMAND);
        harness.givenFetch(ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    private List<ServiceCommandType> observationTypes(TrancheTransition transition) {
        return transition.getObservations().stream().map(ServiceCommand::getType).toList();
    }


    private TrancheTransition handle(DealContext context) {
        return harness.entrySubmitted().handle(context, context.getDeal().getTranches().getFirst());
    }

    /** Нога налита целиком, живой эпизод есть. */
    private DealContext confirmedEntryContext() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "5", "0");
        subject.getOrders().add(filledEntryLeg(30L, TRANCHE_ID, "5"));
        DealContext context = contextOf(Deal.Status.ACTIVE, subject);
        context.getDeal().getPositions().add(livePosition("5"));
        return context;
    }

    private DealContext baseContext() {
        return contextOf(Deal.Status.ACTIVE, submittedTranche());
    }

    /** Транш отправленного входа с живой ногой без наливов. */
    private DealTranche submittedTranche() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED);
        subject.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        return subject;
    }

    private DealContext contextOf(Deal.Status status, DealTranche subject) {
        return contextBuilder(deal(status, subject)).build();
    }

    private List<ServiceCommandType> commandTypes(TrancheTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
