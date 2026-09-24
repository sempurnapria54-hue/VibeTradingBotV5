package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.balance;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.leg;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.minutesAgo;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Предвходовая проверка транша — группа {@code U17} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/TranchePrecheckHandler.md §«Входные проверки» и
 * §«Рабочая логика»).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ACTIVE}, транш {@code PRECHECK}
 * с объявлением; чужого живого риска нет, живого эпизода нет; снимок
 * средств свежий; рабочий блок подменён и молчит; диспозиция настоящая.
 *
 * <p><b>Кейс {@code U17.11} не прогоняется</b>: дом объявляет запасное
 * значение причины закрытия транша ненаписуемым, а код пишет его
 * фолбэком (находка {@code F-2}) — двух носителей на одно ожидание
 * достаточно, чтобы ожидания не было.
 *
 * <p><b>Граница свежести РОВНО на пороге кейса не получает:</b> предикат
 * читает часы процесса напрямую, и детерминизм ему даёт размах — момент
 * снимка ставится далеко по обе стороны толерантности.
 */
class TranchePrecheckPassTest {

    private final TrancheHarness harness = new TrancheHarness();

    @Test
    @DisplayName("U17.1 — живого риска у транша нет: терминал с причиной «условие входа истекло»")
    void u17_1_aCandidateWithoutRiskClosesOnTheExpiredEntryCondition() {
        TrancheTransition transition = handle(baseContext());

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        assertThat(transition.hasCommands()).isFalse();
    }

    @Test
    @DisplayName("U17.2 — у транша живой риск: просьба увести сделку ошибочной тропой")
    void u17_2_aCandidateCarryingRiskEscalates() {
        DealTranche candidate = precheckTranche();
        candidate.getOrders().add(leg(30L, TRANCHE_ID, Order.Status.CREATED, Boolean.FALSE, "0"));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, candidate));

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U17.3 — рабочий блок выдал команду: ребра нет, вход ещё не отправлен")
    void u17_3_aWorkCommandTravelsWithoutAnEdge() {
        harness.givenWork(TrancheTransition.command(command(ServiceCommandType.CREATE_ORDER_COMMAND)));

        TrancheTransition transition = handle(baseContext());

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.CREATE_ORDER_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U17.4 — команда и уже отправленная нога: команда И ребро в отправленный вход")
    void u17_4_aWorkCommandWithASubmittedLegAlsoCarriesTheEdge() {
        DealTranche candidate = precheckTranche();
        candidate.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        harness.givenWork(TrancheTransition.command(command(ServiceCommandType.SUBMIT_ORDER_COMMAND)));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, candidate));

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.SUBMIT_ORDER_COMMAND);
        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.ENTRY_SUBMITTED);
    }

    @Test
    @DisplayName("U17.5 — рабочий блок сам двинул статус: второе ребро не дописывается")
    void u17_5_anEdgeFromTheWorkBlockIsKept() {
        DealTranche candidate = precheckTranche();
        candidate.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        harness.givenWork(TrancheTransition.close(DealTranche.CloseReason.RISK_CONTROL));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, candidate));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.RISK_CONTROL);
    }

    @Test
    @DisplayName("U17.6 — блок молчит, а нога отправлена: ребро в отправленный вход без команд")
    void u17_6_aSubmittedLegMovesTheStatusWithoutCommands() {
        DealTranche candidate = precheckTranche();
        candidate.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, candidate));

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.ENTRY_SUBMITTED);
        assertThat(transition.hasCommands()).isFalse();
    }

    @Test
    @DisplayName("U17.7 — чужой живой риск на сделке: блок не запускался")
    void u17_7_aForeignLiveRiskEscalatesBeforeTheWorkBlock() {
        DealContext context = baseContext();
        context.getDeal().getOrders().add(liveEntryLeg(90L, null));

        TrancheTransition transition = handle(context);

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.hasCommands()).isFalse();
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U17.8 — живых эпизодов два: та же просьба")
    void u17_8_twoLiveEpisodesEscalateBeforeTheWorkBlock() {
        DealContext context = baseContext();
        context.getDeal().getPositions().add(livePosition("1"));
        context.getDeal().getPositions().add(livePosition("1"));

        TrancheTransition transition = handle(context);

        assertThat(transition.getDealErrorRequested()).isTrue();
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U17.9 — объявления у транша нет: в предвходовой проверке оно обязательно")
    void u17_9_anAbsentDeclarationEscalates() {
        DealTranche candidate = precheckTranche();
        candidate.setStrategyTrancheId(null);

        TrancheTransition transition = handle(contextOf(Deal.Status.ACTIVE, candidate));

        assertThat(transition.getDealErrorRequested()).isTrue();
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U17.10 — сделка сворачивается: терминал с НАСЛЕДОВАННОЙ причиной сделки")
    void u17_10_aCollapsingDealClosesTheCandidateWithItsInheritedReason() {
        DealContext context = contextOf(Deal.Status.EXIT_PENDING, precheckTranche());
        context.getDeal().setCloseReason(Deal.CloseReason.STRATEGY_EXIT);

        TrancheTransition transition = handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.STRATEGY_EXIT);
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U17.12 — снимка средств нет вовсе: команда добычи, блок не запускался")
    void u17_12_anAbsentBalanceSnapshotRequestsItsFetch() {
        harness.givenBalanceFetchCommand();
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, precheckTranche())).build();

        TrancheTransition transition = handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_BALANCE_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U17.13 — толерантность возраста не объявлена: снимок несвеж, команда добычи")
    void u17_13_anAbsentFreshnessToleranceStalesTheSnapshot() {
        harness.givenBalanceFreshness(null);
        harness.givenBalanceFetchCommand();
        DealContext context = contextWithBalance(minutesAgo(0));

        TrancheTransition transition = handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_BALANCE_COMMAND);
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U17.14 — снимок старше толерантности на порядок: команда добычи снимка")
    void u17_14_aStaleSnapshotRequestsItsFetch() {
        harness.givenBalanceFetchCommand();
        DealContext context = contextWithBalance(minutesAgo(50));

        TrancheTransition transition = handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_BALANCE_COMMAND);
        harness.verifyWorkPassNotRun();
    }

    @Test
    @DisplayName("U17.15 — снимок моложе толерантности на порядок: проход идёт к рабочему блоку")
    void u17_15_aFreshSnapshotLetsTheWorkBlockRun() {
        harness.givenBalanceFetchCommand();
        DealContext context = contextWithBalance(minutesAgo(0));

        TrancheTransition transition = handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
    }

    // --- сборка ------------------------------------------------------------

    private TrancheTransition handle(DealContext context) {
        return harness.precheck().handle(context, context.getDeal().getTranches().getFirst());
    }

    private DealContext baseContext() {
        return contextWithBalance(minutesAgo(0));
    }

    private DealContext contextWithBalance(OffsetDateTime updatedAt) {
        return contextBuilder(deal(Deal.Status.ACTIVE, precheckTranche()))
                .balanceContainer(balance(updatedAt))
                .build();
    }

    private DealContext contextOf(Deal.Status status, DealTranche candidate) {
        return contextBuilder(deal(status, candidate))
                .balanceContainer(balance(minutesAgo(0)))
                .build();
    }

    /** Кандидат без ног, налива и защит. */
    private DealTranche precheckTranche() {
        return tranche(TRANCHE_ID, DealTranche.Status.PRECHECK);
    }

    private List<ServiceCommandType> commandTypes(TrancheTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
