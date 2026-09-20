package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.deal;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Агрегат сделки: терминальность, сворачивание, покрытие, уровень —
 * группа `U6` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/deal-lifecycle.json, {@code isTerminal},
 * {@code allTranchesTerminal}; docs/spec/risk-limits.json,
 * {@code dealCollapsing}; docs/spec/protection-coverage.json,
 * {@code allTranchesCovered}, {@code dealRiskBearing},
 * {@code dealStopUnresolved}, {@code stopCurrentLive}).
 *
 * <p><b>Базовая сборка:</b> сделка со статусом, направлением и коллекцией
 * траншей, каждый из которых собран как в U1-U4. <b>Подмены траншевых
 * предикатов нет ни одной</b> — агрегат считается по настоящим траншам.
 */
class DealAggregateTest {

    @Test
    @DisplayName("U6.1 — статус штатного терминала")
    void u6_1_closedIsTerminal() {
        assertThat(deal(Deal.Status.CLOSED).isTerminal()).isTrue();
    }

    @Test
    @DisplayName("U6.2 — статус аварийного терминала")
    void u6_2_emergencyClosedIsTerminal() {
        assertThat(deal(Deal.Status.EMERGENCY_CLOSED).isTerminal()).isTrue();
    }

    /** Обработка аварийной тропы ещё идёт. */
    @Test
    @DisplayName("U6.3 — ошибочное состояние")
    void u6_3_errorIsNotTerminal() {
        assertThat(deal(Deal.Status.ERROR).isTerminal()).isFalse();
    }

    @Test
    @DisplayName("U6.4 — статус координированного выхода")
    void u6_4_exitPendingIsCollapsing() {
        assertThat(deal(Deal.Status.EXIT_PENDING).isCollapsing()).isTrue();
    }

    /** Вторая половина окна закрыта недостижимостью акта, а не этим предикатом. */
    @Test
    @DisplayName("U6.5 — ошибочное состояние")
    void u6_5_errorIsNotCollapsing() {
        assertThat(deal(Deal.Status.ERROR).isCollapsing()).isFalse();
    }

    @Test
    @DisplayName("U6.6 — все транши терминальны")
    void u6_6_allTranchesTerminal() {
        assertThat(deal(Deal.Status.ACTIVE, closedTranche(), closedTranche()).allTranchesTerminal()).isTrue();
    }

    /** Пустой перечень удовлетворяет всеобщности; полноту графа ставит читатель. */
    @Test
    @DisplayName("U6.7 — коллекция траншей пуста")
    void u6_7_anEmptyTrancheListSatisfiesTheUniversal() {
        assertThat(deal(Deal.Status.ACTIVE).allTranchesTerminal()).isTrue();
    }

    @Test
    @DisplayName("U6.8 — один транш несёт живой риск, остальные нет")
    void u6_8_anyTrancheRiskBearing() {
        assertThat(deal(Deal.Status.ACTIVE, quietTranche(), riskBearingTranche()).anyTrancheRiskBearing())
                .isTrue();
    }

    /** Перечень не сужается до живых. */
    @Test
    @DisplayName("U6.9 — все транши покрыты, среди них терминальные")
    void u6_9_allTranchesCoveredIncludesTerminalOnes() {
        assertThat(deal(Deal.Status.ACTIVE, coveredTranche(), closedTranche()).allTranchesCovered()).isTrue();
    }

    @Test
    @DisplayName("U6.10 — один транш не покрыт")
    void u6_10_oneUncoveredBreaksTheAggregate() {
        assertThat(deal(Deal.Status.ACTIVE, coveredTranche(), riskBearingTranche()).allTranchesCovered())
                .isFalse();
    }

    /** Агрегат отказывает вычислением, а не берёт уровень соседа. */
    @Test
    @DisplayName("U6.11 — хоть один транш с экспозицией уровня не несёт")
    void u6_11_anUnresolvedTrancheEmptiesTheDealLevel() {
        Deal subject = deal(Deal.Status.ACTIVE, trancheWithLevel("90"), riskBearingTranche());
        subject.setDirection(StrategyTradeDirection.LONG);

        assertThat(subject.currentStopLevel()).isNull();
    }

    @Test
    @DisplayName("U6.12 — все транши с экспозицией уровень несут, направление длинное")
    void u6_12_longTakesTheLowestTrancheLevel() {
        Deal subject = deal(Deal.Status.ACTIVE, trancheWithLevel("90"), trancheWithLevel("95"));
        subject.setDirection(StrategyTradeDirection.LONG);

        assertThat(subject.currentStopLevel()).isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("U6.13 — то же, направление короткое")
    void u6_13_shortTakesTheHighestTrancheLevel() {
        Deal subject = deal(Deal.Status.ACTIVE, trancheWithLevel("90"), trancheWithLevel("95"));
        subject.setDirection(StrategyTradeDirection.SHORT);

        assertThat(subject.currentStopLevel()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("U6.14 — смесь активных и терминальных траншей")
    void u6_14_liveTranchesAreExactlyTheActiveOnes() {
        DealTranche active = quietTranche();
        active.setStatus(DealTranche.Status.MANAGING);
        Deal subject = deal(Deal.Status.ACTIVE, active, closedTranche());

        assertThat(subject.liveTranches()).containsExactly(active);
    }

    @Test
    @DisplayName("U6.15 — живая заявка сделки, не приписанная ни одному траншу")
    void u6_15_anUnattributedLiveOrderIsRisk() {
        Deal subject = deal(Deal.Status.ACTIVE, quietTranche());
        subject.setOrders(List.of(order(1L, Order.Status.ACTIVE, null, false)));

        assertThat(subject.unattributedLiveRisk()).isTrue();
    }

    @Test
    @DisplayName("U6.16 — все живые заявки сделки приписаны траншам")
    void u6_16_attributedLiveOrdersAreNotRisk() {
        Order live = order(1L, Order.Status.ACTIVE, null, false);
        DealTranche tranche = trancheOf(trancheWithExposure(null), List.of(live), List.of());
        tranche.setStatus(DealTranche.Status.MANAGING);
        Deal subject = deal(Deal.Status.ACTIVE, tranche);
        subject.setOrders(List.of(live));

        assertThat(subject.unattributedLiveRisk()).isFalse();
    }

    /** Пустые ключи отсеиваются только при сборке множества приписанных. */
    @Test
    @DisplayName("U6.17 — живая заявка без ключа строки у сделки и у транша")
    void u6_17_aKeylessLiveOrderIsNeverAttributed() {
        Order live = order(null, Order.Status.ACTIVE, null, false);
        DealTranche tranche = trancheOf(trancheWithExposure(null), List.of(live), List.of());
        Deal subject = deal(Deal.Status.ACTIVE, tranche);
        subject.setOrders(List.of(live));

        assertThat(subject.unattributedLiveRisk()).isTrue();
    }

    @Test
    @DisplayName("U6.18 — причина заведения — восстановление, исполненных входов нет")
    void u6_18_recoveryAloneMarksAnObservedPosition() {
        Deal subject = deal(Deal.Status.ACTIVE, quietTranche());
        subject.setEntryReason(Deal.EntryReason.RECOVERY);

        assertThat(subject.positionObserved()).isTrue();
    }

    @Test
    @DisplayName("U6.19 — причина заведения — стратегия, у одного транша состоялся вход")
    void u6_19_anEntryFillMarksAnObservedPosition() {
        Deal subject = deal(Deal.Status.ACTIVE, quietTranche(), trancheWithExposure("10"));
        subject.setEntryReason(Deal.EntryReason.STRATEGY);

        assertThat(subject.positionObserved()).isTrue();
    }

    @Test
    @DisplayName("U6.20 — причина заведения — стратегия, входов не состоялось")
    void u6_20_noFillsNoObservedPosition() {
        Deal subject = deal(Deal.Status.ACTIVE, quietTranche());
        subject.setEntryReason(Deal.EntryReason.STRATEGY);

        assertThat(subject.positionObserved()).isFalse();
    }

    /**
     * Сделочные перечни обходят СВОИ коллекции, а не коллекции траншей
     * (пробел `G4` документа, добран под-шагом 3).
     */
    @Test
    @DisplayName("U6.21 — живые заявки сделки против живых заявок транша")
    void u6_21_dealLiveOrdersTraverseTheDealCollection() {
        Order ofDeal = order(1L, Order.Status.ACTIVE, null, false);
        Order ofTranche = order(2L, Order.Status.ACTIVE, null, false);
        Deal subject = deal(Deal.Status.ACTIVE,
                trancheOf(trancheWithExposure(null), List.of(ofTranche), List.of()));
        subject.setOrders(List.of(ofDeal));

        assertThat(subject.liveOrders()).containsExactly(ofDeal);
    }

    /** Та же ось у отдельных условных заявок (пробел `G4`). */
    @Test
    @DisplayName("U6.22 — живые отдельные условные заявки сделки")
    void u6_22_dealLiveAlgoOrdersTraverseTheDealCollection() {
        AlgoOrder ofDeal = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");
        AlgoOrder ofTranche = standaloneStop(2L, AlgoOrder.Status.ACTIVE, "10", "90");
        Deal subject = deal(Deal.Status.ACTIVE,
                trancheOf(trancheWithExposure(null), List.of(), List.of(ofTranche)));
        subject.setAlgoOrders(List.of(ofDeal));

        assertThat(subject.liveAlgoOrders()).containsExactly(ofDeal);
    }

    /** Та же ось у встроенных защит (пробел `G4`). */
    @Test
    @DisplayName("U6.23 — живые встроенные защиты сделки")
    void u6_23_dealLiveAttachedProtectionsTraverseTheDealOrders() {
        AttachedAlgoOrder ofDeal = attached(AttachedAlgoOrder.Status.ACTIVE, "10", "90");
        AttachedAlgoOrder ofTranche = attached(AttachedAlgoOrder.Status.ACTIVE, "10", "80");
        Deal subject = deal(Deal.Status.ACTIVE, trancheOf(trancheWithExposure(null),
                List.of(orderWith(order(2L, Order.Status.ACTIVE, "10", false), ofTranche)), List.of()));
        subject.setOrders(List.of(orderWith(order(1L, Order.Status.ACTIVE, "10", false), ofDeal)));

        assertThat(subject.liveAttachedProtections()).containsExactly(ofDeal);
    }

    /**
     * Агрегатный признак нерезолвимости уровня, прочитанный ПРЯМО
     * (пробел `G5` документа, добран под-шагом 3).
     */
    @Test
    @DisplayName("U6.24 — признак нерезолвимости уровня сделки спрашивается прямо")
    void u6_24_dealStopUnresolvedAnswersOnItsOwn() {
        assertThat(deal(Deal.Status.ACTIVE, trancheWithLevel("90")).stopUnresolved()).isFalse();
        assertThat(deal(Deal.Status.ACTIVE, trancheWithLevel("90"), riskBearingTranche()).stopUnresolved())
                .isTrue();
    }

    private static DealTranche closedTranche() {
        DealTranche tranche = trancheOf(trancheWithExposure(null), List.of(), List.of());
        tranche.setStatus(DealTranche.Status.CLOSED);
        return tranche;
    }

    /** Экспозиции нет, живых заявок нет — риска не несёт, покрыт тривиально. */
    private static DealTranche quietTranche() {
        return trancheOf(trancheWithExposure(null), List.of(), List.of());
    }

    /** Экспозиция есть, защиты нет — риск несёт, не покрыт, уровень не резолвится. */
    private static DealTranche riskBearingTranche() {
        return trancheOf(trancheWithExposure("10"), List.of(), List.of());
    }

    /** Экспозиция есть и полностью покрыта защитой без уровня расхождения. */
    private static DealTranche coveredTranche() {
        return trancheWithLevel("90");
    }

    private static DealTranche trancheWithLevel(String level) {
        return trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", level)));
    }
}
