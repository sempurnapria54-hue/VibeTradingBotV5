package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.DECLARATION_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.attachedProtection;
import static com.example.tradingcore.unit.fsm.FsmFixture.cancelledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.declaration;
import static com.example.tradingcore.unit.fsm.FsmFixture.detail;
import static com.example.tradingcore.unit.fsm.FsmFixture.filledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.leg;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.protectiveAction;
import static com.example.tradingcore.unit.fsm.FsmFixture.step;
import static com.example.tradingcore.unit.fsm.FsmFixture.strategyRow;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.util.Constants;
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
 *
 * <p><b>Налитая нога несёт встроенную защиту.</b> Выходная проверка
 * покрытия читается на каждом проходе, кроме сворачивания, и нога с
 * наливом без защиты была бы потерей покрытия, а не штатным входом. Живая
 * и снятая частично налитые ноги несут защиту в постановке — её покрытие
 * засчитывается отложенным (docs/rules/live-risk-protection.md).
 *
 * <p><b>Клетки выходной проверки покрытия меток не несут</b> — их метки
 * назначает документ кейсов
 * (.claude/decisions/submitted-entry-lost-attached-protection.md).
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
        context.getDeal().getUnattributedOrders().add(liveEntryLeg(90L, null));

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
    @DisplayName("U18.17 — сворачивание, ноги нет, наследуемой причины нет: ошибочная тропа, ребра и причины нет")
    void u18_17_aCollapsingDealWithoutAnInheritableReasonEscalates() {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED);
        subject.getOrders().add(cancelledEntryLeg(30L, TRANCHE_ID));

        TrancheTransition transition = handle(contextOf(Deal.Status.EXIT_PENDING, subject));

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.getNextStatus()).isNull();
        assertThat(transition.getCloseReason()).isNull();
        assertThat(transition.hasCommands()).isFalse();
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
        Order partial = leg(30L, TRANCHE_ID, Order.Status.PARTIALLY_COMPLETED, Boolean.FALSE, "2");
        partial.getAttachedAlgoOrders().add(placingProtection(60L));
        subject.getOrders().add(partial);
        DealContext context = contextOf(Deal.Status.ACTIVE, subject);
        context.getDeal().getPositions().add(livePosition("2"));

        TrancheTransition transition = handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.getDealErrorRequested())
                .as("защита живой частично налитой ноги в постановке — покрытие отложенное, не нарушение")
                .isFalse();
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

    @Test
    @DisplayName("U18.15 — нога снята после частичного налива при живом эпизоде: команда звена консолидации")
    void u18_15_aLegCancelledAfterAPartialFillIsConsolidated() {
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION,
                ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        givenFetches();
        DealContext context = contextOf(Deal.Status.ACTIVE, partiallyFilledCancelledTranche());
        context.getDeal().getPositions().add(livePosition("2"));

        TrancheTransition transition = handle(context);

        assertThat(commandTypes(transition))
                .containsExactly(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        assertThat(transition.hasObservations()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U18.16 — сделка сворачивается, нога снята после частичного налива: ребро в выход, не в терминал")
    void u18_16_aCollapsingDealSendsAFilledTrancheToTheExit() {
        DealContext context = contextOf(Deal.Status.EXIT_PENDING, partiallyFilledCancelledTranche());
        context.getDeal().getPositions().add(livePosition("2"));
        context.getDeal().setCloseReason(Deal.CloseReason.STOP_LOSS);

        TrancheTransition transition = handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
        assertThat(transition.getCloseReason()).isNull();
    }

    // --- выходная проверка покрытия ------------------------------------------

    @Test
    @DisplayName("Нога снята после частичного налива, защита не встала, обязательства нет: ступень 2 до консолидации")
    void aLostProtectionOnACancelledPartialLegEscalatesBeforeConsolidation() {
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION,
                ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        DealContext context = contextOf(Deal.Status.ACTIVE, lostProtectionTranche());
        context.getDeal().getPositions().add(livePosition("2"));

        TrancheTransition transition = handle(context);

        assertUncoveredEscalation(transition);
        assertThat(transition.hasCommands()).as("консолидация не затребована").isFalse();
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("Та же потеря защиты при живом обязательстве покрытия: ход продолжается консолидацией")
    void aLiveCoverageCommitmentLetsTheConsolidationThrough() {
        harness.givenSystemCommand(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION,
                ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, lostProtectionTranche()))
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE,
                        DealTranche.Status.ENTRY_SUBMITTED,
                        step(1L, StrategyStepType.MAIN_PROTECTION, protectiveAction(5L)))))
                .actionStates(List.of(strategyRow(5L, TRANCHE_ID, 1, DealActionStateStatus.SUBMITTED)))
                .build();
        context.getDeal().getPositions().add(livePosition("2"));

        TrancheTransition transition = handle(context);

        assertThat(transition.getDealErrorRequested()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
        assertThat(commandTypes(transition))
                .containsExactly(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
    }

    @Test
    @DisplayName("Живая нога без налива с отказавшей защитой: экспозиции нет — нарушения нет, добыча идёт")
    void aFailedProtectionWithoutAFillIsNotAViolation() {
        givenFetches();
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED);
        Order live = liveEntryLeg(30L, TRANCHE_ID);
        live.getAttachedAlgoOrders().add(failedProtection(60L));
        subject.getOrders().add(live);

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, subject));

        assertThat(transition.getDealErrorRequested()).isFalse();
        assertThat(transition.getHoldSignal()).isNull();
        assertThat(observationTypes(transition)).containsExactly(ServiceCommandType.REFRESH_ORDER_COMMAND,
                ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    @Test
    @DisplayName("Сделка сворачивается при потерянной защите налитой ноги: ребро в выход, гейт покрытия не спрашивался")
    void aCollapsingDealSendsTheTrancheToTheExitWithoutTheCoverageCheck() {
        DealContext context = contextOf(Deal.Status.EXIT_PENDING, lostProtectionTranche());
        context.getDeal().getPositions().add(livePosition("2"));
        context.getDeal().setCloseReason(Deal.CloseReason.STOP_LOSS);

        TrancheTransition transition = handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
        assertThat(transition.getDealErrorRequested()).isFalse();
        harness.verifyCoverageGateNotAsked();
    }

    @Test
    @DisplayName("Перевыставленный вход: новая нога жива, защита снятой налитой ноги не встала — ступень 2")
    void aReplacedEntryWithTheLostProtectionOfTheCancelledLegEscalates() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "3", "0");
        subject.getOrders().add(cancelledPartialLeg(30L, "3", failedProtection(60L)));
        subject.getOrders().add(liveEntryLeg(31L, TRANCHE_ID));
        DealContext context = contextOf(Deal.Status.ACTIVE, subject);
        context.getDeal().getPositions().add(livePosition("3"));

        TrancheTransition transition = handle(context);

        assertUncoveredEscalation(transition);
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("Перевыставленный вход, защита снятой ноги ещё в постановке: добываются обе ноги, позиция последней")
    void aReplacedEntryObservesTheCarrierOfAPlacingProtectionTogetherWithTheLiveLeg() {
        givenFetches();
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "3", "0");
        subject.getOrders().add(cancelledPartialLeg(30L, "3", placingProtection(60L)));
        subject.getOrders().add(liveEntryLeg(31L, TRANCHE_ID));
        DealContext context = contextOf(Deal.Status.ACTIVE, subject);
        context.getDeal().getPositions().add(livePosition("3"));

        TrancheTransition transition = handle(context);

        assertThat(transition.getDealErrorRequested()).isFalse();
        assertThat(observationTypes(transition)).containsExactly(ServiceCommandType.REFRESH_ORDER_COMMAND,
                ServiceCommandType.REFRESH_ORDER_COMMAND, ServiceCommandType.REFRESH_POSITION_COMMAND);
        assertThat(transition.getObservations().subList(0, 2).stream()
                .map(observation -> ((RefreshOrderCommandPayload) observation.getPayload()).getOrderId())
                .toList())
                .containsExactly(30L, 31L);
    }

    // --- сборка ------------------------------------------------------------

    /** Транш с входной ногой, снятой после частичного налива; её защита ещё в постановке. */
    private DealTranche partiallyFilledCancelledTranche() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "2", "0");
        subject.getOrders().add(cancelledPartialLeg(30L, "2", placingProtection(60L)));
        return subject;
    }

    /** Транш с входной ногой, снятой после частичного налива; её защита не встала. */
    private DealTranche lostProtectionTranche() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "2", "0");
        subject.getOrders().add(cancelledPartialLeg(30L, "2", failedProtection(60L)));
        return subject;
    }

    /** Входная нога, снятая после частичного налива, со встроенной защитой. */
    private Order cancelledPartialLeg(Long id, String fill, AttachedAlgoOrder protection) {
        Order leg = leg(id, TRANCHE_ID, Order.Status.CANCELED, Boolean.FALSE, fill);
        leg.setCloseReason(Order.CloseReason.CANCELED_BY_STRATEGY);
        leg.getAttachedAlgoOrders().add(protection);
        return leg;
    }

    /** Встроенная защита в постановке: площадка её ещё не показала. */
    private AttachedAlgoOrder placingProtection(Long id) {
        AttachedAlgoOrder protection = attachedProtection(id, "5");
        protection.setStatus(AttachedAlgoOrder.Status.PENDING);
        return protection;
    }

    /** Встроенная защита, которая на площадке не встала: отказ постановки. */
    private AttachedAlgoOrder failedProtection(Long id) {
        AttachedAlgoOrder protection = attachedProtection(id, "5");
        protection.setStatus(AttachedAlgoOrder.Status.ERROR);
        protection.setCloseReason(AttachedAlgoOrder.CloseReason.PROTECTION_PLACEMENT_FAILED);
        return protection;
    }

    /** Исход нарушения покрытия: ошибочная тропа вместе с биржевой ступенью 2. */
    private void assertUncoveredEscalation(TrancheTransition transition) {
        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.getHoldSignal().getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(transition.getHoldSignal().getRung()).isEqualTo(HoldRung.HARD);
        assertThat(transition.getHoldSignal().getCode()).isEqualTo(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
    }

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

    /** Нога налита целиком и несёт встроенную защиту, живой эпизод есть. */
    private DealContext confirmedEntryContext() {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.ENTRY_SUBMITTED), "5", "0");
        Order filled = filledEntryLeg(30L, TRANCHE_ID, "5");
        filled.getAttachedAlgoOrders().add(attachedProtection(60L, "5"));
        subject.getOrders().add(filled);
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
