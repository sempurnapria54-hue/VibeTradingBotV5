package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.coverageOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneTakeProfit;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Покрытие транша и законность снятия защиты — группа `U2` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/protection-coverage.json, величины {@code trancheCoverage},
 * {@code trancheCovered}, {@code coveredSize},
 * {@code coverageAfterRemoval}, {@code removalAllowed};
 * docs/rules/live-risk-protection.md).
 *
 * <p><b>Базовая сборка:</b> транш с экспозицией из U1; входная заявка с
 * наливом и встроенной защитой в живом статусе; отдельная условная заявка
 * в биржевом живом статусе с типом, несущим уровень. Всё — настоящими
 * полями моделей.
 *
 * <p>Само покрытие читается публичной поверхностью — исключением
 * идентичности, которой у защит транша нет.
 */
class TrancheCoverageTest {

    /** Вклад встроенной защиты ограничен наливом родителя, а не её объявлением. */
    @Test
    @DisplayName("U2.1 — встроенная защита объявлена больше налива родителя")
    void u2_1_attachedContributionIsCappedByParentFill() {
        DealTranche subject = trancheOf(trancheWithExposure("4"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "4", false),
                        attached(AttachedAlgoOrder.Status.ACTIVE, "10", "90"))),
                List.of());

        assertThat(coverageOf(subject)).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("U2.2 — встроенная защита объявлена меньше налива родителя")
    void u2_2_attachedContributionIsItsDeclaredSize() {
        DealTranche subject = trancheOf(trancheWithExposure("10"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false),
                        attached(AttachedAlgoOrder.Status.ACTIVE, "3", "90"))),
                List.of());

        assertThat(coverageOf(subject)).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("U2.3 — отдельная защита объявлена и частично сработала")
    void u2_3_standaloneContributionDropsByTheTriggeredRemainder() {
        AlgoOrder protection = standaloneStop(1L, AlgoOrder.Status.PARTIALLY_COMPLETED, "10", "90");
        protection.setExternalSize(dec("4"));
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(), List.of(protection));

        assertThat(coverageOf(subject)).isEqualByComparingTo("6");
    }

    /** Покрытием считается только защита, несущая действующий уровень. */
    @Test
    @DisplayName("U2.4 — отдельная защита с типом тейк-профита")
    void u2_4_takeProfitDoesNotCover() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneTakeProfit(1L, AlgoOrder.Status.ACTIVE, "10")));

        assertThat(coverageOf(subject)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Созданная локально на биржу не отправлена — покрытием не является. */
    @Test
    @DisplayName("U2.5 — отдельная защита в локально созданном статусе")
    void u2_5_locallyCreatedProtectionDoesNotCover() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.CREATED, "10", "90")));

        assertThat(coverageOf(subject)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Сравнение нестрогое. */
    @Test
    @DisplayName("U2.6 — покрытие в точности равно экспозиции")
    void u2_6_equalCoverageSatisfiesTheInvariant() {
        DealTranche subject = trancheOf(trancheWithExposure("6"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "6", "90")));

        assertThat(subject.isCovered()).isTrue();
    }

    @Test
    @DisplayName("U2.7 — покрытие на минимальную величину меньше экспозиции")
    void u2_7_aMinimalShortfallBreaksTheInvariant() {
        DealTranche subject = trancheOf(trancheWithExposure("6.000000001"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "6", "90")));

        assertThat(subject.isCovered()).isFalse();
    }

    /** Покрывать нечего, и пара «риска нет, не покрыт» не производится. */
    @Test
    @DisplayName("U2.8 — экспозиции нет, живых защит нет")
    void u2_8_noExposureIsCoveredTrivially() {
        DealTranche subject = trancheOf(trancheWithExposure(null), List.of(), List.of());

        assertThat(subject.isCovered()).isTrue();
    }

    @Test
    @DisplayName("U2.9 — две отдельные защиты, снимается одна")
    void u2_9_removedProtectionLeavesTheOther() {
        DealTranche subject = trancheOf(trancheWithExposure("12"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "5", "90"),
                        standaloneStop(2L, AlgoOrder.Status.ACTIVE, "7", "90")));

        assertThat(subject.coverageWithoutAlgoOrder(1L)).isEqualByComparingTo("7");
    }

    /** Сравнение ключей отождествляет пустоту с пустотой. */
    @Test
    @DisplayName("U2.10 — снимаемая защита ещё не имеет ключа строки, вторая имеет")
    void u2_10_aKeylessRemovalLeavesTheKeyedNeighbour() {
        DealTranche subject = trancheOf(trancheWithExposure("12"), List.of(),
                List.of(standaloneStop(null, AlgoOrder.Status.ACTIVE, "5", "90"),
                        standaloneStop(2L, AlgoOrder.Status.ACTIVE, "7", "90")));

        assertThat(subject.coverageWithoutAlgoOrder(null)).isEqualByComparingTo("7");
    }

    /**
     * Предикат исключает ВСЯКУЮ защиту с пустым ключом, а не только
     * снимаемую. Направление ошибки консервативное — покрытие
     * занижается, снятие запрещается (звено `Z4`).
     */
    @Test
    @DisplayName("U2.17 — снимаемая защита без ключа и соседняя тоже без ключа")
    void u2_17_allKeylessProtectionsDropOutTogether() {
        DealTranche subject = trancheOf(trancheWithExposure("12"), List.of(),
                List.of(standaloneStop(null, AlgoOrder.Status.ACTIVE, "5", "90"),
                        standaloneStop(null, AlgoOrder.Status.ACTIVE, "7", "90")));

        assertThat(subject.coverageWithoutAlgoOrder(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("U2.11 — остающееся покрытие не ниже экспозиции")
    void u2_11_removalIsAllowed() {
        DealTranche subject = presentedTranche("5");

        assertThat(subject.removalAllowed(1L)).isTrue();
    }

    @Test
    @DisplayName("U2.12 — остающееся покрытие ниже экспозиции")
    void u2_12_removalIsRefused() {
        DealTranche subject = presentedTranche("9");

        assertThat(subject.removalAllowed(1L)).isFalse();
    }

    /** Отказ ВЫЧИСЛЕНИЕМ: пустота нулём не подменяется. */
    @Test
    @DisplayName("U2.13 — живая защита есть, заявок у транша не предъявлено ни одной")
    void u2_13_removalIsUndecidableWithoutPresentedOrders() {
        DealTranche subject = trancheOf(trancheWithExposure("5"), null,
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "5", "90")));

        assertThat(subject.removalAllowed(1L)).isNull();
    }

    /** Отказ стои́т на нуле ПРЕДЪЯВЛЕННЫХ заявок, а не на нуле экспозиции. */
    @Test
    @DisplayName("U2.14 — живая защита есть, заявки предъявлены, экспозиция нулевая")
    void u2_14_zeroExposureWithPresentedOrdersIsDecidable() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(10L, Order.Status.COMPLETED, null, false)),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "5", "90")));

        assertThat(subject.removalAllowed(1L)).isTrue();
    }

    @Test
    @DisplayName("U2.15 — встроенная защита терминальна при живой отдельной")
    void u2_15_terminalAttachedContributesNothing() {
        DealTranche subject = trancheOf(trancheWithExposure("10"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false),
                        attached(AttachedAlgoOrder.Status.COMPLETED, "10", "90"))),
                List.of(standaloneStop(2L, AlgoOrder.Status.ACTIVE, "4", "90")));

        assertThat(subject.hasLiveProtection()).isTrue();
        assertThat(coverageOf(subject)).isEqualByComparingTo("4");
    }

    /** Два предиката отвечают на разные вопросы. */
    @Test
    @DisplayName("U2.16 — живой только отдельный тейк-профит")
    void u2_16_liveProtectionIsNotTheSameAsProtectionWithLevel() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneTakeProfit(1L, AlgoOrder.Status.ACTIVE, "10")));

        assertThat(subject.hasLiveProtection()).isTrue();
        assertThat(subject.hasStandaloneProtection()).isFalse();
    }

    /** Транш с предъявленными заявками и двумя отдельными защитами (5 и 7). */
    private static DealTranche presentedTranche(String exposure) {
        return trancheOf(trancheWithExposure(exposure),
                List.of(order(10L, Order.Status.COMPLETED, null, false)),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "5", "90"),
                        standaloneStop(2L, AlgoOrder.Status.ACTIVE, "7", "90")));
    }
}
