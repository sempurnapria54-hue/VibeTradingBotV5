package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.DEAL_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Переход сделки разрешён целиком — группа {@code U5} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/deal-lifecycle.json, величина {@code transitionAllowed}).
 *
 * <p><b>Базовая сборка.</b> Состояние {@code U3.1}: риск доказанно
 * отсутствует, транш терминален, число и валюта результата стоя́т;
 * предлагаемая цель называется входом кейса.
 *
 * <p><b>Отказ ребра и отказ контракта различает ЖУРНАЛ, а не сам
 * ответ:</b> у обоих он «не разрешён», и запись «ребро не объявлено»
 * пишется только первым.
 */
class DealTransitionAllowedTest {

    private final DealTransitionGate gate = new DealTransitionGate(new DealTerminalGate());

    @Test
    @DisplayName("U5.1 — цель EXIT_PENDING из активной: у нетерминальной цели контракта нет")
    void u5_1_aNonTerminalTargetNeedsNoContract() {
        assertThat(gate.transitionAllowed(context(Deal.Status.ACTIVE), Deal.Status.EXIT_PENDING)).isTrue();
    }

    @Test
    @DisplayName("U5.2 — цель ERROR из активной: контракт не спрашивается")
    void u5_2_theErrorTargetIsAllowedWithoutAContract() {
        assertThat(gate.transitionAllowed(context(Deal.Status.ACTIVE), Deal.Status.ERROR)).isTrue();
    }

    @Test
    @DisplayName("U5.3 — цель CLOSED при выполненном штатном контракте: разрешён")
    void u5_3_theCleanTerminalIsAllowedWhenItsContractHolds() {
        assertThat(gate.transitionAllowed(context(Deal.Status.ACTIVE), Deal.Status.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("U5.4 — цель CLOSED при пустом числе: отказал контракт, а не матрица")
    void u5_4_theCleanTerminalIsRefusedByTheContractWithoutAMatrixRecord() {
        DealContext context = context(Deal.Status.ACTIVE);
        context.getDeal().setResultProfit(null);

        Boolean allowed;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTransitionGate.class)) {
            allowed = gate.transitionAllowed(context, Deal.Status.CLOSED);
            messages = capture.messages();
        }

        assertThat(allowed).isFalse();
        assertThat(messages).noneMatch(message -> message.startsWith("Deal edge is not declared"));
    }

    @Test
    @DisplayName("U5.5 — цель EMERGENCY_CLOSED из ошибочного при отсутствии риска: разрешён")
    void u5_5_theEmergencyTerminalIsAllowedWhenRiskIsProvenAbsent() {
        assertThat(gate.transitionAllowed(context(Deal.Status.ERROR), Deal.Status.EMERGENCY_CLOSED)).isTrue();
    }

    @Test
    @DisplayName("U5.6 — цель EMERGENCY_CLOSED при живой заявке транша: не разрешён")
    void u5_6_theEmergencyTerminalIsRefusedOnLiveRisk() {
        DealContext context = context(Deal.Status.ERROR);
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.transitionAllowed(context, Deal.Status.EMERGENCY_CLOSED)).isFalse();
    }

    @Test
    @DisplayName("U5.7 — цель CLOSED из ошибочного: предупреждение «ребро не объявлено» с обоими статусами")
    void u5_7_anUndeclaredEdgeIsRefusedWithAWarning() {
        Boolean allowed;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTransitionGate.class)) {
            allowed = gate.transitionAllowed(context(Deal.Status.ERROR), Deal.Status.CLOSED);
            messages = capture.messages();
        }

        assertThat(allowed).isFalse();
        assertThat(messages).containsExactly(
                "Deal edge is not declared dealId=" + DEAL_ID + " from=ERROR to=CLOSED");
    }

    @Test
    @DisplayName("U5.8 — цель равна текущему статусу: то же предупреждение")
    void u5_8_aSelfLoopIsRefusedWithTheSameWarning() {
        Boolean allowed;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTransitionGate.class)) {
            allowed = gate.transitionAllowed(context(Deal.Status.ACTIVE), Deal.Status.ACTIVE);
            messages = capture.messages();
        }

        assertThat(allowed).isFalse();
        assertThat(messages).containsExactly(
                "Deal edge is not declared dealId=" + DEAL_ID + " from=ACTIVE to=ACTIVE");
    }

    /** Базовая сборка группы в названном статусе сделки. */
    private DealContext context(Deal.Status status) {
        DealTranche closed = fills(tranche(TRANCHE_ID, DealTranche.Status.CLOSED), "5", "5");
        Deal deal = deal(status, closed);
        deal.setResultProfit(new BigDecimal("10"));
        deal.setResultProfitCurrency("USDT");
        return contextBuilder(deal).build();
    }
}
