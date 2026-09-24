package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.deal;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.episode;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.tranche;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Вывод слагаемых экспозиции транша из наблюдённых фактов — продолжение
 * группы `U1` документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/protection-coverage.json, величины
 * {@code trancheEntryFilled}, {@code trancheReduceOnlyFilled},
 * {@code trancheProtectionClosed}, {@code trancheCloseAttributed},
 * {@code dealInTeardown}).
 *
 * <p><b>Базовая сборка:</b> в отличие от клеток {@code U1.1}-{@code U1.8},
 * слагаемые не ставятся полями, а выводятся из заявок, защит и эпизода —
 * ровно тем ходом, которым их выводит сборка графа прохода. Поля, стоящие
 * на транше до вывода, намеренно ложны: вывод обязан их перекрыть.
 */
class TrancheExposureDerivationTest {

    @Test
    @DisplayName("U1.9 — налив входа и reduce-only выводится из ног; нога с пустым намерением не входит ни в один")
    void u1_9_ownFillsAreDerivedFromTheLegs() {
        DealTranche subject = trancheOf(tranche("99", "99", null, null), List.of(
                order(1L, Order.Status.COMPLETED, "5", Boolean.FALSE),
                order(2L, Order.Status.COMPLETED, "2", Boolean.TRUE),
                order(3L, Order.Status.COMPLETED, "3", null)), new ArrayList<>());

        subject.deriveOwnFills();

        assertThat(subject.getEntryFilled()).isEqualByComparingTo("5");
        assertThat(subject.getReduceOnlyFilled()).isEqualByComparingTo("2");
        assertThat(subject.grossExposure()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("U1.10 — закрытый защитами объём: сработавшая встроенная размером, отдельная фактическим размером")
    void u1_10_protectionClosedCountsTriggeredAttachedAndStandaloneActualSize() {
        AlgoOrder triggered = standaloneStop(20L, AlgoOrder.Status.COMPLETED, "4", "90");
        triggered.setExternalSize(dec("2"));
        DealTranche subject = trancheOf(tranche(null, null, "99", null), List.of(
                orderWith(order(1L, Order.Status.COMPLETED, "5", Boolean.FALSE),
                        attached(AttachedAlgoOrder.Status.COMPLETED, "1", "90"),
                        attached(AttachedAlgoOrder.Status.ACTIVE, "4", "90"))),
                List.of(triggered, standaloneStop(21L, AlgoOrder.Status.ACTIVE, "3", "90")));

        subject.deriveOwnFills();

        assertThat(subject.getProtectionClosed()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("U1.11 — координированный выход: закрытое уровнем сделки гасит старший транш первым")
    void u1_11_theDealCloseIsAttributedOldestFirst() {
        DealTranche older = filled(1L, "3");
        DealTranche younger = filled(2L, "2");
        Deal subject = withEpisode(deal(Deal.Status.EXIT_PENDING, older, younger),
                episode(Position.Status.ACTIVE, "1", null));

        subject.deriveTrancheExposures();

        assertThat(older.getCloseAttributed()).isEqualByComparingTo("3");
        assertThat(older.exposure()).isEqualByComparingTo("0");
        assertThat(younger.getCloseAttributed()).isEqualByComparingTo("1");
        assertThat(younger.exposure()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("U1.12 — порядок траншей в коллекции исход не меняет: возраст — идентификатор")
    void u1_12_theAgeIsTheIdentityNotTheCollectionOrder() {
        DealTranche older = filled(1L, "3");
        DealTranche younger = filled(2L, "2");
        Deal subject = withEpisode(deal(Deal.Status.EXIT_PENDING, younger, older),
                episode(Position.Status.ACTIVE, "1", null));

        subject.deriveTrancheExposures();

        assertThat(older.exposure()).isEqualByComparingTo("0");
        assertThat(younger.exposure()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("U1.13 — активная сделка: окна нет, приписанное ноль, недостача остаётся расхождением")
    void u1_13_outsideTheWindowNothingIsAttributed() {
        DealTranche single = filled(1L, "3");
        Deal subject = withEpisode(deal(Deal.Status.ACTIVE, single), episode(Position.Status.ACTIVE, "1", null));

        subject.deriveTrancheExposures();

        assertThat(single.getCloseAttributed()).isEqualByComparingTo("0");
        assertThat(single.exposure()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("U1.14 — ошибочное состояние при плоской позиции: окно открыто, приписано всё")
    void u1_14_anErrorDealWithAFlatPositionAttributesEverything() {
        DealTranche single = filled(1L, "3");
        Deal subject = withEpisode(deal(Deal.Status.ERROR, single), episode(Position.Status.CLOSED, "3", null));

        subject.deriveTrancheExposures();

        assertThat(single.exposure()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U1.15 — ошибочное состояние при живой позиции: окна нет")
    void u1_15_anErrorDealWithALivePositionAttributesNothing() {
        DealTranche single = filled(1L, "3");
        Deal subject = withEpisode(deal(Deal.Status.ERROR, single), episode(Position.Status.ACTIVE, "2", null));

        subject.deriveTrancheExposures();

        assertThat(single.getCloseAttributed()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U1.16 — закрытый эпизод с необнулённым размером нетто-размером не читается")
    void u1_16_aClosedEpisodeSizeIsNotTheNetSize() {
        DealTranche single = filled(1L, "3");
        Deal subject = withEpisode(deal(Deal.Status.EXIT_PENDING, single),
                episode(Position.Status.CLOSED, "3", null));

        subject.deriveTrancheExposures();

        assertThat(single.exposure()).isEqualByComparingTo("0");
    }

    // --- сборка ------------------------------------------------------------

    /** Транш с идентичностью и налитой входной ногой; поля слагаемых намеренно ложны. */
    private DealTranche filled(Long id, String entryFill) {
        DealTranche tranche = trancheOf(tranche("99", null, null, "99"),
                new ArrayList<>(List.of(order(id * 10, Order.Status.COMPLETED, entryFill, Boolean.FALSE))),
                new ArrayList<>());
        tranche.setId(id);
        return tranche;
    }

    private Deal withEpisode(Deal subject, Position episode) {
        subject.setPositions(new ArrayList<>(List.of(episode)));
        return subject;
    }
}
