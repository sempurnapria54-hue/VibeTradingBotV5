package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.DEAL_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.closedPosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.livePosition;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveReduceOnlyLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.protection;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Доказанное отсутствие живого риска — группа {@code U2} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/deal-lifecycle.json, величина {@code riskProvenAbsent};
 * прозой — docs/lifecycles/Deal.md §«Живой риск»).
 *
 * <p><b>Базовая сборка.</b> Сделка с одним траншем; граф предъявлен
 * целиком; живого эпизода нет (строки позиции нет вовсе); транш
 * терминален, экспозиция ноль, живых заявок и защит нет.
 *
 * <p><b>{@code @Tag("debt")}: {@code U2.3} предъявляет находку
 * {@code F-9}</b> — пустой признак полноты графа охрану не поднимает, и
 * «мы не всё прочитали» читается как «риска нет». Ожидание взято из дома
 * (docs/rules/absent-value-semantics.md) и не ослаблено под текущий факт;
 * метка снимется правкой владельца.
 */
class DealRiskProvenAbsentTest {

    private final DealTerminalGate gate = new DealTerminalGate();

    @Test
    @DisplayName("U2.1 — базовая сборка: риск доказанно отсутствует")
    void u2_1_theBaseStateProvesTheAbsenceOfRisk() {
        Deal deal = baseDeal();

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isTrue();
    }

    @Test
    @DisplayName("U2.2 — граф предъявлен НЕ целиком: охрана полнотой стои́т первой")
    void u2_2_anIncompleteGraphRefusesBeforeTheOtherConjuncts() {
        Deal deal = baseDeal();

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.FALSE)).isFalse();
    }

    @Test
    @Tag("debt")
    @DisplayName("U2.3 — полнота графа не объявлена вовсе: пустота читается как «не предъявлен»")
    void u2_3_anAbsentGraphCompletenessReadsAsNotPresented() {
        Deal deal = baseDeal();

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), null)).isFalse();
    }

    @Test
    @DisplayName("U2.4 — живой эпизод с ненулевым внешним размером: ложь")
    void u2_4_aLiveEpisodeRefuses() {
        Deal deal = baseDeal();
        deal.getPositions().add(livePosition("2"));
        exposed(deal.getTranches().getFirst(), "2");

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.5 — строка эпизода закрыта, внешний размер НЕ обнулён: истина")
    void u2_5_aClosedEpisodeWithANonZeroSizeIsNotALiveEpisode() {
        Deal deal = baseDeal();
        deal.getPositions().add(closedPosition("5"));

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isTrue();
    }

    @Test
    @DisplayName("U2.6 — транш несёт ненулевую экспозицию: ложь")
    void u2_6_aTrancheWithExposureRefuses() {
        Deal deal = baseDeal();
        exposed(deal.getTranches().getFirst(), "1");

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.7 — у транша живая входная заявка при нулевой экспозиции: ложь")
    void u2_7_aLiveEntryLegRefuses() {
        Deal deal = baseDeal();
        deal.getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.8 — у транша живая условная заявка при нулевой экспозиции: ложь")
    void u2_8_aLiveConditionalOrderRefuses() {
        Deal deal = baseDeal();
        deal.getTranches().getFirst().getAlgoOrders().add(protection(40L, TRANCHE_ID, "1"));

        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.9 — живая заявка вне множества входных: второй конъюнкт расширен до заявок любого рода")
    void u2_9_aLiveOrderOutsideTheEntrySetStillRefuses() {
        Deal deal = baseDeal();
        DealTranche tranche = deal.getTranches().getFirst();
        tranche.getOrders().add(liveReduceOnlyLeg(31L, TRANCHE_ID));

        assertThat(tranche.isRiskBearing()).isFalse();
        assertThat(gate.anyLiveOrder(deal.getTranches())).isTrue();
        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.10 — сумма экспозиций 1 без живого эпизода: сверка не сходится с нулём")
    void u2_10_aPositiveExposureWithoutALiveEpisodeDoesNotReconcile() {
        Deal deal = baseDeal();
        exposed(deal.getTranches().getFirst(), "1");

        assertThat(gate.exposureReconciled(null, deal.getTranches())).isFalse();
        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.11 — сумма 2 при живом эпизоде размера 2: сверка сходится, конъюнкт эпизода ложен")
    void u2_11_aReconciledExposureStillLosesToTheLiveEpisodeConjunct() {
        Deal deal = baseDeal();
        exposed(deal.getTranches().getFirst(), "2");
        deal.getPositions().add(livePosition("2"));

        assertThat(gate.exposureReconciled(deal.livePosition(), deal.getTranches())).isTrue();
        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.12 — траншей нет вовсе: сумма по пустому перечню равна нулю")
    void u2_12_aDealWithoutTranchesReconcilesAtZero() {
        Deal deal = deal(Deal.Status.EXIT_PENDING);

        assertThat(gate.dealExposure(deal.getTranches())).isEqualByComparingTo("0");
        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isTrue();
    }

    @Test
    @DisplayName("U2.13 — экспозиции 3 и −3: сверка сходится, а поштучный признак риска — нет")
    void u2_13_oppositeExposuresReconcileWhileTheRiskPredicateStaysTrue() {
        DealTranche first = exposed(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "3");
        DealTranche second = fills(tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING), "0", "3");
        Deal deal = deal(Deal.Status.EXIT_PENDING, first, second);

        assertThat(gate.exposureReconciled(null, deal.getTranches())).isTrue();
        assertThat(gate.dealRiskBearing(deal.getTranches())).isTrue();
        assertThat(gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE)).isFalse();
    }

    @Test
    @DisplayName("U2.14 — сверка не сходится: ложь И запись лога с суммой экспозиций")
    void u2_14_anUnreconciledExposureIsTheOnlyConjunctThatLogs() {
        DealTranche negative = fills(tranche(TRANCHE_ID, DealTranche.Status.CLOSED), "0", "3");
        Deal deal = deal(Deal.Status.EXIT_PENDING, negative);

        List<String> messages;
        Boolean proven;
        try (FsmLogCapture capture = FsmLogCapture.attach(DealTerminalGate.class)) {
            proven = gate.riskProvenAbsent(deal, deal.getTranches(), Boolean.TRUE);
            messages = capture.messages();
        }

        assertThat(proven).isFalse();
        assertThat(messages).containsExactly(
                "Exposure is not reconciled dealId=" + DEAL_ID + " dealExposure=-3");
    }

    /**
     * Базовая сборка группы: один терминальный транш без налива, ног и
     * защит; эпизодов у сделки нет.
     */
    private Deal baseDeal() {
        DealTranche terminal = tranche(TRANCHE_ID, DealTranche.Status.CLOSED);
        return deal(Deal.Status.EXIT_PENDING, terminal);
    }
}
