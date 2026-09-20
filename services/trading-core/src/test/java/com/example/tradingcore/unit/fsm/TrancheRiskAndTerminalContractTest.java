package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.protection;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.fsm.TrancheTransitionGate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Новый риск под сворачиванием и контракт терминала транша — группа
 * {@code U14} документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/rules/exit-teardown-order.md;
 * docs/spec/deal-tranche-lifecycle.json, величины
 * {@code riskCreatingUnderCollapse} и {@code terminalContract}).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ACTIVE}; транш с нулевой
 * экспозицией, без живых заявок и защит; граф полон.
 *
 * <p><b>Энфорсер запрета стои́т на ЦЕЛИ перехода, а не на паре:</b> в
 * отправленный вход приходят два разных ребра, и гейт по паре пришлось
 * бы дублировать.
 */
class TrancheRiskAndTerminalContractTest {

    private final TrancheTransitionGate gate = new TrancheTransitionGate();

    @Test
    @DisplayName("U14.1 — цель ENTRY_SUBMITTED при активной сделке: нового риска под сворачиванием нет")
    void u14_1_anEntryUnderAnActiveDealIsNotRiskCreatingUnderCollapse() {
        Deal active = deal(Deal.Status.ACTIVE, bareTranche());

        assertThat(gate.riskCreatingUnderCollapse(active, DealTranche.Status.ENTRY_SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("U14.2 — цель ENTRY_SUBMITTED под сворачиванием: переход запрещён с предупреждением")
    void u14_2_anEntryUnderCollapseIsRefusedWithAWarning() {
        DealTranche subject = bareTranche();
        DealContext context = contextBuilder(deal(Deal.Status.EXIT_PENDING, subject)).build();

        Boolean creating = gate.riskCreatingUnderCollapse(context.getDeal(),
                DealTranche.Status.ENTRY_SUBMITTED);
        Boolean allowed;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(TrancheTransitionGate.class)) {
            allowed = gate.transitionAllowed(context, subject, DealTranche.Status.ENTRY_SUBMITTED);
            messages = capture.messages();
        }

        assertThat(creating).isTrue();
        assertThat(allowed).isFalse();
        assertThat(messages).containsExactly(
                "Tranche takes new risk under deal collapse trancheId=" + TRANCHE_ID + " to=ENTRY_SUBMITTED");
    }

    @Test
    @DisplayName("U14.3 — цель EXIT_PENDING под сворачиванием: сворачивание запрещает лишь набор риска")
    void u14_3_anExitTargetUnderCollapseIsNotRiskCreating() {
        Deal collapsing = deal(Deal.Status.EXIT_PENDING, bareTranche());

        assertThat(gate.riskCreatingUnderCollapse(collapsing, DealTranche.Status.EXIT_PENDING)).isFalse();
    }

    @Test
    @DisplayName("U14.4 — цель ENTRY_SUBMITTED при ошибочной сделке: вторая половина окна недостижима")
    void u14_4_anErrorStatusIsOutsideTheCollapseWindowByUnreachability() {
        Deal failing = deal(Deal.Status.ERROR, bareTranche());

        assertThat(gate.riskCreatingUnderCollapse(failing, DealTranche.Status.ENTRY_SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("U14.5 — базовая сборка: контракт терминала выполнен")
    void u14_5_theBaseStateSatisfiesTheTerminalContract() {
        assertThat(gate.terminalContract(bareTranche(), Boolean.TRUE)).isTrue();
    }

    @Test
    @DisplayName("U14.6 — граф предъявлен не целиком: охрана полнотой стои́т первой")
    void u14_6_anIncompleteGraphRefusesTheTerminalContract() {
        assertThat(gate.terminalContract(bareTranche(), Boolean.FALSE)).isFalse();
    }

    @Test
    @DisplayName("U14.7 — экспозиция транша 1: контракт не выполнен")
    void u14_7_aNonZeroExposureRefusesTheTerminalContract() {
        assertThat(gate.terminalContract(exposed(bareTranche(), "1"), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U14.8 — живая входная нога: контракт не выполнен")
    void u14_8_aLiveEntryLegRefusesTheTerminalContract() {
        DealTranche subject = bareTranche();
        subject.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.terminalContract(subject, Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U14.9 — живая условная заявка при нулевой экспозиции: третий дизъюнкт не избыточен")
    void u14_9_aLiveConditionalOrderRefusesTheTerminalContract() {
        DealTranche subject = bareTranche();
        subject.getAlgoOrders().add(protection(40L, TRANCHE_ID, "1"));

        assertThat(gate.terminalContract(subject, Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U14.10 — цель CLOSED при выполненном контракте и объявленном ребре: разрешён")
    void u14_10_theTerminalIsAllowedWhenItsContractHolds() {
        DealTranche exiting = tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING);
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, exiting)).build();

        assertThat(gate.transitionAllowed(context, exiting, DealTranche.Status.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("U14.11 — цель CLOSED при невыполненном контракте: записи об необъявленном ребре нет")
    void u14_11_theTerminalIsRefusedByItsContractWithoutAMatrixRecord() {
        DealTranche exiting = exposed(tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), "1");
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, exiting)).build();

        Boolean allowed;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(TrancheTransitionGate.class)) {
            allowed = gate.transitionAllowed(context, exiting, DealTranche.Status.CLOSED);
            messages = capture.messages();
        }

        assertThat(allowed).isFalse();
        assertThat(messages).noneMatch(message -> message.startsWith("Tranche edge is not declared"));
    }

    @Test
    @DisplayName("U14.12 — цель, отличная от терминала: контракт гейтит только терминал")
    void u14_12_aNonTerminalTargetIsAllowedWithoutTheContract() {
        DealTranche managed = exposed(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "1");
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE, managed)).build();

        assertThat(gate.terminalContract(managed, Boolean.TRUE)).isFalse();
        assertThat(gate.transitionAllowed(context, managed, DealTranche.Status.EXIT_PENDING)).isTrue();
    }

    /** Транш сопровождения без налива, ног и защит. */
    private DealTranche bareTranche() {
        return tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
    }
}
