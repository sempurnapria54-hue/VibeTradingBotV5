package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Живой риск транша, его перечни и входная нога — группа `U4` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/protection-coverage.json, величины
 * {@code trancheRiskBearing}, {@code trancheHasLiveEntryOrder};
 * docs/spec/deal-tranche-lifecycle.json, {@code isTrancheTerminal},
 * {@code isTrancheActive}; docs/spec/deal-context-load.json,
 * {@code entrySubmitted}).
 *
 * <p><b>Базовая сборка:</b> транш со статусом жизненного цикла;
 * коллекции заявок и отдельных условных заявок — с настоящими статусами;
 * встроенные защиты — на своих родительских заявках.
 */
class TrancheRiskBearingTest {

    @Test
    @DisplayName("U4.1 — статус закрытого транша")
    void u4_1_closedIsTerminalAndNotActive() {
        DealTranche subject = statusOnly(DealTranche.Status.CLOSED);

        assertThat(subject.isTerminal()).isTrue();
        assertThat(subject.isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DealTranche.Status.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U4.2 — любой нетерминальный статус")
    void u4_2_anyNonTerminalStatusIsActive(DealTranche.Status status) {
        assertThat(statusOnly(status).isActive()).isTrue();
    }

    /** Активность требует непустого статуса. */
    @Test
    @DisplayName("U4.3 — статус пуст")
    void u4_3_anAbsentStatusIsNeitherActiveNorTerminal() {
        DealTranche subject = statusOnly(null);

        assertThat(subject.isActive()).isFalse();
        assertThat(subject.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("U4.4 — экспозиция положительна, живых заявок нет")
    void u4_4_exposureAloneBearsRisk() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(), List.of());

        assertThat(subject.isRiskBearing()).isTrue();
    }

    @Test
    @DisplayName("U4.5 — экспозиции нет, живая входная нога есть")
    void u4_5_aLiveEntryLegBearsRisk() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(1L, Order.Status.ACTIVE, null, false)), List.of());

        assertThat(subject.isRiskBearing()).isTrue();
    }

    /** Третий дизъюнкт читается носителем защиты, а не перечнем отдельных заявок. */
    @Test
    @DisplayName("U4.6 — живая встроенная защита на терминальной родительской заявке")
    void u4_6_aLiveAttachedProtectionBearsRisk() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false),
                        attached(AttachedAlgoOrder.Status.ACTIVE, "10", "90"))),
                List.of());

        assertThat(subject.isRiskBearing()).isTrue();
    }

    @Test
    @DisplayName("U4.7 — экспозиции нет, живых заявок нет, живых защит нет")
    void u4_7_nothingBearsRisk() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(1L, Order.Status.CANCELED, null, false)), List.of());

        assertThat(subject.isRiskBearing()).isFalse();
    }

    @Test
    @DisplayName("U4.8 — смесь живых и терминальных заявок")
    void u4_8_theLiveListsHoldExactlyTheLiveMembers() {
        Order live = order(1L, Order.Status.ACTIVE, null, false);
        Order finished = order(2L, Order.Status.COMPLETED, null, false);
        AlgoOrder liveAlgo = standaloneStop(3L, AlgoOrder.Status.PENDING, "10", "90");
        AlgoOrder finishedAlgo = standaloneStop(4L, AlgoOrder.Status.CANCELED, "10", "90");
        DealTranche subject = trancheOf(trancheWithExposure("10"),
                List.of(live, finished), List.of(liveAlgo, finishedAlgo));

        assertThat(subject.liveOrders()).containsExactly(live);
        assertThat(subject.liveAlgoOrders()).containsExactly(liveAlgo);
    }

    /** Обход идёт по ВСЕМ заявкам, а не по живым. */
    @Test
    @DisplayName("U4.9 — живая встроенная защита на терминальной родительской заявке")
    void u4_9_attachedProtectionsSurviveTheParentTerminal() {
        AttachedAlgoOrder protection = attached(AttachedAlgoOrder.Status.ACTIVE, "10", "90");
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(orderWith(order(1L, Order.Status.CANCELED, "10", false), protection)),
                List.of());

        assertThat(subject.liveAttachedProtections()).containsExactly(protection);
    }

    @Test
    @DisplayName("U4.10 — коллекции заявок пусты (значения нет вовсе)")
    void u4_10_absentCollectionsGiveEmptyLists() {
        DealTranche subject = trancheOf(trancheWithExposure(null), null, null);

        assertThatCode(subject::liveOrders).doesNotThrowAnyException();
        assertThat(subject.liveOrders()).isEmpty();
        assertThat(subject.liveAlgoOrders()).isEmpty();
        assertThat(subject.liveAttachedProtections()).isEmpty();
    }

    /** Вход различается доменным намерением, а не типом заявки. */
    @Test
    @DisplayName("U4.11 — живая заявка, помеченная только уменьшающей позицию")
    void u4_11_aReducingLegIsNotAnEntryLeg() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(1L, Order.Status.ACTIVE, null, true)), List.of());

        assertThat(subject.hasLiveEntryOrder()).isFalse();
    }

    @Test
    @DisplayName("U4.12 — живая заявка, не помеченная только уменьшающей")
    void u4_12_aNonReducingLiveLegIsAnEntryLeg() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(1L, Order.Status.ACTIVE, null, false)), List.of());

        assertThat(subject.hasLiveEntryOrder()).isTrue();
    }

    /** Живость при отборе не спрашивается: у наливившейся ноги читают якорь. */
    @Test
    @DisplayName("U4.13 — две не-уменьшающие заявки с разными ключами строк")
    void u4_13_theEntryLegIsTheLastKeyedNonReducingOrder() {
        Order older = order(1L, Order.Status.ACTIVE, null, false);
        Order newer = order(2L, Order.Status.COMPLETED, "10", false);
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(older, newer), List.of());

        assertThat(subject.entryOrder()).isSameAs(newer);
    }

    @Test
    @DisplayName("U4.14 — не-уменьшающая заявка без ключа строки при второй с ключом")
    void u4_14_aKeylessOrderStaysOutOfTheSelection() {
        Order keyless = order(null, Order.Status.ACTIVE, null, false);
        Order keyed = order(1L, Order.Status.ACTIVE, null, false);
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(keyless, keyed), List.of());

        assertThat(subject.entryOrder()).isSameAs(keyed);
    }

    @Test
    @DisplayName("U4.15 — не-уменьшающих заявок нет вовсе")
    void u4_15_noEntryLegAtAll() {
        DealTranche subject = trancheOf(trancheWithExposure("10"),
                List.of(order(1L, Order.Status.ACTIVE, null, true)), List.of());

        assertThat(subject.entryOrder()).isNull();
    }

    @Test
    @DisplayName("U4.16 — входная нога в локально созданном статусе")
    void u4_16_aLocallyCreatedEntryIsNotSubmitted() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(1L, Order.Status.CREATED, null, false)), List.of());

        assertThat(subject.entrySubmitted()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Order.Status.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U4.17 — входная нога в любом статусе сверх локально созданного")
    void u4_17_anythingBeyondCreatedIsSubmitted(Order.Status status) {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(1L, status, null, false)), List.of());

        assertThat(subject.entrySubmitted()).isTrue();
    }

    @Test
    @DisplayName("U4.18 — входной ноги нет")
    void u4_18_noEntryLegIsNotSubmitted() {
        DealTranche subject = trancheOf(trancheWithExposure(null), List.of(), List.of());

        assertThat(subject.entrySubmitted()).isFalse();
    }

    private static DealTranche statusOnly(DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setStatus(status);
        return tranche;
    }
}
