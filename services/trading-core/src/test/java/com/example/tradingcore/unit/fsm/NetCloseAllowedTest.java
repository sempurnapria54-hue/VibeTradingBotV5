package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveReduceOnlyLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Разрешение полного закрытия нетто-экспозиции — группа {@code U4}
 * документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/deal-lifecycle.json, величина {@code netCloseAllowed}; довод
 * порядка — docs/rules/exit-teardown-order.md).
 *
 * <p><b>Базовая сборка.</b> Сделка в координированном выходе, два транша;
 * граф предъявлен целиком; живых входных ног нет ни у одного транша.
 */
class NetCloseAllowedTest {

    private final DealTransitionGate gate = new DealTransitionGate(new DealTerminalGate());

    @Test
    @DisplayName("U4.1 — базовая сборка: закрытие разрешено")
    void u4_1_theBaseStateAllowsTheNetClose() {
        assertThat(gate.netCloseAllowed(baseContext())).isTrue();
    }

    @Test
    @DisplayName("U4.2 — у первого транша живая входная нога: закрытие гонялось бы за наливом")
    void u4_2_aLiveEntryLegOnTheFirstTrancheRefuses() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.netCloseAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U4.3 — у второго транша живая входная нога: перечень берёт ЛЮБОЙ транш")
    void u4_3_aLiveEntryLegOnTheSecondTrancheRefusesToo() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getLast().getOrders().add(liveEntryLeg(31L, SECOND_TRANCHE_ID));

        assertThat(gate.netCloseAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U4.4 — граф предъявлен не целиком: «живой ноги нет» ложно молча")
    void u4_4_anIncompleteGraphRefuses() {
        DealContext context = contextBuilder(twoTrancheDeal()).graphComplete(Boolean.FALSE).build();

        assertThat(gate.netCloseAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U4.5 — живая reduce-only нога: предикат берёт входные ноги, а не заявки вообще")
    void u4_5_aLiveReduceOnlyLegDoesNotBlockTheNetClose() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveReduceOnlyLeg(32L, TRANCHE_ID));

        assertThat(gate.netCloseAllowed(context)).isTrue();
    }

    @Test
    @DisplayName("U4.6 — живая входная нога у ТЕРМИНАЛЬНОГО транша: перечень не сужается до живых")
    void u4_6_aLiveEntryLegOnAClosedTrancheStillRefuses() {
        DealTranche closed = tranche(SECOND_TRANCHE_ID, DealTranche.Status.CLOSED);
        closed.getOrders().add(liveEntryLeg(33L, SECOND_TRANCHE_ID));
        DealContext context = contextBuilder(deal(Deal.Status.EXIT_PENDING,
                tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING), closed)).build();

        assertThat(gate.netCloseAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U4.7 — траншей нет вовсе: живых ног нет ни у кого")
    void u4_7_aDealWithoutTranchesAllowsTheNetClose() {
        DealContext context = contextBuilder(deal(Deal.Status.EXIT_PENDING)).build();

        assertThat(gate.netCloseAllowed(context)).isTrue();
    }

    private DealContext baseContext() {
        return contextBuilder(twoTrancheDeal()).build();
    }

    private Deal twoTrancheDeal() {
        return deal(Deal.Status.EXIT_PENDING,
                tranche(TRANCHE_ID, DealTranche.Status.EXIT_PENDING),
                tranche(SECOND_TRANCHE_ID, DealTranche.Status.EXIT_PENDING));
    }
}
