package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.at;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Рёбра статуса: матрица, write-once, отказ броском — группа `U11`
 * документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/algo-order-lifecycle.json, {@code algoTransitionAllowed},
 * {@code reasonRequired}, {@code transitionAccepted};
 * docs/spec/order-lifecycle.json, {@code attachedTransitionAllowed},
 * {@code orderTransitionAllowed}; docs/lifecycles/Order.md §«Разбор
 * истории»).
 *
 * <p><b>Базовая сборка:</b> заявка, условная заявка либо встроенная
 * защита в объявленном исходном статусе; причина финализации — пустая
 * либо уже проставленная.
 *
 * <p><b>Группа НОВОЙ ОСИ: выход у всех её строк — состояние получателя
 * после вызова.</b> Отказ называется РЕБРОМ, а не фактом броска: пара
 * «откуда, куда» названа в строке, и статус после отказа обязан остаться
 * прежним — несостоявшийся переход не оставляет модель наполовину
 * переведённой. Матрицы ниже взяты из ДОМА (исполнимые спецификации), а
 * не списаны с реализации.
 */
class StatusEdgeTest {

    /** Матрица рёбер отдельной условной заявки — docs/spec/algo-order-lifecycle.json. */
    private static final Map<AlgoOrder.Status, Set<AlgoOrder.Status>> ALGO_MATRIX = Map.of(
            AlgoOrder.Status.CREATED, EnumSet.of(AlgoOrder.Status.PENDING, AlgoOrder.Status.CANCELED,
                    AlgoOrder.Status.ERROR),
            AlgoOrder.Status.PENDING, EnumSet.of(AlgoOrder.Status.ACTIVE, AlgoOrder.Status.COMPLETED,
                    AlgoOrder.Status.CANCELED, AlgoOrder.Status.ERROR),
            AlgoOrder.Status.ACTIVE, EnumSet.of(AlgoOrder.Status.PARTIALLY_COMPLETED,
                    AlgoOrder.Status.COMPLETED, AlgoOrder.Status.CANCELED, AlgoOrder.Status.ERROR),
            AlgoOrder.Status.PARTIALLY_COMPLETED, EnumSet.of(AlgoOrder.Status.COMPLETED,
                    AlgoOrder.Status.CANCELED, AlgoOrder.Status.ERROR));

    /** Матрица рёбер встроенной защиты — docs/spec/order-lifecycle.json. */
    private static final Map<AttachedAlgoOrder.Status, Set<AttachedAlgoOrder.Status>> ATTACHED_MATRIX = Map.of(
            AttachedAlgoOrder.Status.CREATED, EnumSet.of(AttachedAlgoOrder.Status.PENDING,
                    AttachedAlgoOrder.Status.CANCELED, AttachedAlgoOrder.Status.ERROR),
            AttachedAlgoOrder.Status.PENDING, EnumSet.of(AttachedAlgoOrder.Status.ACTIVE,
                    AttachedAlgoOrder.Status.CANCELED, AttachedAlgoOrder.Status.ERROR),
            AttachedAlgoOrder.Status.ACTIVE, EnumSet.of(AttachedAlgoOrder.Status.COMPLETED,
                    AttachedAlgoOrder.Status.CANCELED, AttachedAlgoOrder.Status.ERROR));

    /**
     * Пустое «откуда» допускает только созданный, и ПЕРЕВОДЯЩЕГО метода в
     * этот статус нет ни у одной из двух моделей: локально созданный
     * ставит сборка модели, а не ребро.
     */
    @Test
    @DisplayName("U11.1 — встроенная защита: статус пуст, допустимость перевода в созданный")
    void u11_1_theCreationEdgeIsAskedByPredicateNotByTransition() {
        AttachedAlgoOrder subject = attached(null, "5", "90");

        assertThat(subject.canTransitionTo(AttachedAlgoOrder.Status.CREATED)).isTrue();
        assertThat(subject.getStatus()).isNull();
        assertThat(publicTransitionNames(AttachedAlgoOrder.class)).doesNotContain("toCreate");
        assertThat(publicTransitionNames(AlgoOrder.class)).doesNotContain("toCreate");
    }

    @Test
    @DisplayName("U11.2 — условная заявка: статус пуст, перевод в любой иной")
    void u11_2_anEmptyOriginRefusesEveryOtherTarget() {
        for (AlgoOrder.Status target : transitionTargets()) {
            AlgoOrder subject = standaloneStop(1L, null, "10", "90");

            assertThatThrownBy(() -> applyAlgoTransition(subject, target))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(subject.getStatus()).isNull();
            assertThat(subject.getCloseReason()).isNull();
        }
    }

    @ParameterizedTest(name = "U11.3 — {0} -> {1}")
    @MethodSource("allowedAlgoEdges")
    @DisplayName("U11.3 — каждое ребро матрицы с публичным переводящим методом")
    void u11_3_everyAllowedAlgoEdgeMovesTheStatus(AlgoOrder.Status from, AlgoOrder.Status to) {
        AlgoOrder subject = standaloneStop(1L, from, "10", "90");

        applyAlgoTransition(subject, to);

        assertThat(subject.getStatus()).isEqualTo(to);
        if (AlgoOrder.Status.COMPLETED.equals(to)) {
            assertThat(subject.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.TRIGGERED);
        }
    }

    @ParameterizedTest(name = "U11.4 — {0} -> {1}")
    @MethodSource("forbiddenAlgoEdges")
    @DisplayName("U11.4 — каждая пара вне матрицы порознь, в том числе из терминального")
    void u11_4_everyForbiddenAlgoEdgeLeavesTheModelUntouched(AlgoOrder.Status from, AlgoOrder.Status to) {
        AlgoOrder subject = standaloneStop(1L, from, "10", "90");
        subject.setCloseReason(AlgoOrder.CloseReason.UNKNOWN);

        assertThatThrownBy(() -> applyAlgoTransition(subject, to)).isInstanceOf(IllegalStateException.class);
        assertThat(subject.getStatus()).isEqualTo(from);
        assertThat(subject.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.UNKNOWN);
    }

    /** Проверка причины стои́т ДО перевода. */
    @Test
    @DisplayName("U11.5 — условная заявка: отмена без причины")
    void u11_5_cancellationWithoutReasonRefusesBeforeTheMove() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");

        assertThatThrownBy(() -> subject.toCancel(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
    }

    @Test
    @DisplayName("U11.6 — условная заявка: перевод в ошибочное состояние без причины")
    void u11_6_errorWithoutReasonRefusesBeforeTheMove() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");

        assertThatThrownBy(() -> subject.toError(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
    }

    /** Поле write-once: повтор причину не переписывает. */
    @Test
    @DisplayName("U11.7 — условная заявка: причина уже стои́т, переводится с другой")
    void u11_7_theCloseReasonIsWriteOnce() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");
        subject.setCloseReason(AlgoOrder.CloseReason.MISSING_AFTER_REFRESH);

        subject.toCancel(AlgoOrder.CloseReason.KILL_SWITCH);

        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.CANCELED);
        assertThat(subject.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.MISSING_AFTER_REFRESH);
    }

    /** Частично сработавшая входит в живые — закрытой она не считается. */
    @Test
    @DisplayName("U11.8 — условная заявка: частичное срабатывание")
    void u11_8_partialCompletionLeavesTheReasonEmpty() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");

        subject.toPartiallyComplete();

        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.PARTIALLY_COMPLETED);
        assertThat(subject.getCloseReason()).isNull();
        assertThat(subject.isLive()).isTrue();
    }

    @ParameterizedTest(name = "U11.9 — {0} -> {1}")
    @MethodSource("allowedAttachedEdges")
    @DisplayName("U11.9 — встроенная защита: каждое ребро своей матрицы")
    void u11_9_everyAllowedAttachedEdgeMovesTheStatus(AttachedAlgoOrder.Status from,
                                                     AttachedAlgoOrder.Status to) {
        AttachedAlgoOrder subject = attached(from, "5", "90");

        applyAttachedTransition(subject, to);

        assertThat(subject.getStatus()).isEqualTo(to);
        assertThat(EnumSet.allOf(AttachedAlgoOrder.Status.class))
                .noneMatch(status -> "PARTIALLY_COMPLETED".equals(status.name()));
    }

    @Test
    @DisplayName("U11.10 — встроенная защита: пара вне матрицы")
    void u11_10_aForbiddenAttachedEdgeIsRefusedByPredicateAndByTransition() {
        AttachedAlgoOrder subject = attached(AttachedAlgoOrder.Status.CREATED, "5", "90");

        assertThat(subject.canTransitionTo(AttachedAlgoOrder.Status.ACTIVE)).isFalse();
        assertThatThrownBy(subject::toActive).isInstanceOf(IllegalStateException.class);
        assertThat(subject.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CREATED);
    }

    @Test
    @DisplayName("U11.11 — отправленная защита, наблюдённый завершённый терминал")
    void u11_11_anObservedCompletionPassesThroughActive() {
        AttachedAlgoOrder subject = attached(AttachedAlgoOrder.Status.PENDING, "5", "90");

        subject.applyObservedTerminal(AttachedAlgoOrder.Status.COMPLETED,
                AttachedAlgoOrder.CloseReason.TRIGGERED);

        assertThat(subject.getStatus()).isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(subject.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
    }

    /** Ребро в отмену из отправленного в матрице есть — активации не происходит. */
    @Test
    @DisplayName("U11.12 — отправленная защита, наблюдённая отмена")
    void u11_12_anObservedCancellationNeedsNoIntermediateActivation() {
        AttachedAlgoOrder subject = attached(AttachedAlgoOrder.Status.PENDING, "5", "90");

        assertThat(subject.canTransitionTo(AttachedAlgoOrder.Status.CANCELED)).isTrue();
        subject.applyObservedTerminal(AttachedAlgoOrder.Status.CANCELED,
                AttachedAlgoOrder.CloseReason.KILL_SWITCH);

        assertThat(subject.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(subject.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.KILL_SWITCH);
    }

    @Test
    @DisplayName("U11.13 — терминальная защита, применяется наблюдённый терминал")
    void u11_13_anObservedTerminalOnATerminalIsRefused() {
        AttachedAlgoOrder subject = attached(AttachedAlgoOrder.Status.COMPLETED, "5", "90");
        subject.setCloseReason(AttachedAlgoOrder.CloseReason.TRIGGERED);

        assertThatThrownBy(() -> subject.applyObservedTerminal(AttachedAlgoOrder.Status.CANCELED,
                AttachedAlgoOrder.CloseReason.KILL_SWITCH)).isInstanceOf(IllegalStateException.class);
        assertThat(subject.getStatus()).isEqualTo(AttachedAlgoOrder.Status.COMPLETED);
        assertThat(subject.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.TRIGGERED);
    }

    @Test
    @DisplayName("U11.14 — обычная заявка: перевод в завершённый")
    void u11_14_anOrderCompletionCarriesTheFilledReason() {
        Order subject = order(1L, Order.Status.ACTIVE, "10", false);

        subject.toComplete();

        assertThat(subject.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(subject.getCloseReason()).isEqualTo(Order.CloseReason.FILLED);
    }

    @Test
    @DisplayName("U11.15 — обычная заявка: перевод в отмену без причины")
    void u11_15_anOrderCancellationRequiresAReason() {
        Order subject = order(1L, Order.Status.ACTIVE, "10", false);

        assertThatThrownBy(() -> subject.toCancel(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(subject.getStatus()).isEqualTo(Order.Status.ACTIVE);
    }

    @Test
    @DisplayName("U11.16 — обычная заявка: причина уже стои́т, ставится вторая")
    void u11_16_theOrderCloseReasonIsWriteOnce() {
        Order subject = order(1L, Order.Status.ACTIVE, "10", false);
        subject.setCloseReason(Order.CloseReason.CANCELED_BY_STRATEGY);

        subject.toCancel(Order.CloseReason.KILL_SWITCH);

        assertThat(subject.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(subject.getCloseReason()).isEqualTo(Order.CloseReason.CANCELED_BY_STRATEGY);
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Матрица
     * {@code orderTransitionAllowed} (docs/spec/order-lifecycle.json) из
     * завершённого статуса не допускает ни одного ребра, а модель
     * переводит статус БЕЗУСЛОВНО: охранника у обычной заявки нет ни
     * одного, хотя у двух её соседей по тому же жизненному циклу охрана
     * стои́т на самой модели (находка `D-7`, `.claude/work/backlog.md`
     * §«Матрица рёбер обычной заявки не исполняется ни одним носителем»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U11.17 — обычная заявка: перевод из завершённого в отмену")
    void u11_17_aForbiddenOrderEdgeIsRefused() {
        Order subject = order(1L, Order.Status.COMPLETED, "10", false);

        assertThatThrownBy(() -> subject.toCancel(Order.CloseReason.KILL_SWITCH))
                .isInstanceOf(IllegalStateException.class);
        assertThat(subject.getStatus()).isEqualTo(Order.Status.COMPLETED);
    }

    @Test
    @DisplayName("U11.18 — сделка: порог доказанного покрытия пуст, подаётся момент")
    void u11_18_anEmptyThresholdTakesTheObservedMoment() {
        Deal subject = new Deal();

        subject.advanceCoverageProvenThrough(at(5));

        assertThat(subject.getCoverageProvenThrough()).isEqualTo(at(5));
    }

    @Test
    @DisplayName("U11.19 — сделка: порог стои́т, подаётся более поздний момент")
    void u11_19_aLaterMomentMovesTheThresholdForward() {
        Deal subject = dealProvenThrough(at(5));

        subject.advanceCoverageProvenThrough(at(9));

        assertThat(subject.getCoverageProvenThrough()).isEqualTo(at(9));
    }

    /** Движение только вперёд. */
    @Test
    @DisplayName("U11.20 — сделка: порог стои́т, подаётся более ранний момент")
    void u11_20_anEarlierMomentDoesNotMoveTheThreshold() {
        Deal subject = dealProvenThrough(at(5));

        subject.advanceCoverageProvenThrough(at(1));

        assertThat(subject.getCoverageProvenThrough()).isEqualTo(at(5));
    }

    @Test
    @DisplayName("U11.21 — сделка: порог стои́т, подаётся тот же момент")
    void u11_21_theSameMomentIsIdempotent() {
        Deal subject = dealProvenThrough(at(5));

        subject.advanceCoverageProvenThrough(at(5));

        assertThat(subject.getCoverageProvenThrough()).isEqualTo(at(5));
    }

    @Test
    @DisplayName("U11.22 — сделка: подаётся пустой момент")
    void u11_22_anAbsentMomentIsIgnoredWithoutRefusal() {
        Deal subject = dealProvenThrough(at(5));

        assertThatCode(() -> subject.advanceCoverageProvenThrough(null)).doesNotThrowAnyException();
        assertThat(subject.getCoverageProvenThrough()).isEqualTo(at(5));
    }

    @Test
    @DisplayName("U11.23 — ставка комиссии: подтверждение при пустом счётчике")
    void u11_23_theFirstConfirmationSetsTheCounterToOne() {
        TradeFeeRate subject = feeRate();

        subject.confirm(at(3), "VIP1");

        assertThat(subject.getRefreshCount()).isEqualTo(1L);
        assertThat(subject.getExternalModifiedAt()).isEqualTo(at(3));
        assertThat(subject.getExternalFeeLevel()).isEqualTo("VIP1");
        assertThat(subject.getExternalTakerFeeRate()).isEqualTo("0.0005");
        assertThat(subject.getExternalMakerFeeRate()).isEqualTo("0.0002");
    }

    /** Подтверждение новой строки не заводит. */
    @Test
    @DisplayName("U11.24 — ставка комиссии: подтверждение при непустом счётчике")
    void u11_24_aRepeatedConfirmationGrowsTheCounterByOne() {
        TradeFeeRate subject = feeRate();
        subject.setRefreshCount(4L);

        subject.confirm(at(3), "VIP1");

        assertThat(subject.getRefreshCount()).isEqualTo(5L);
        assertThat(subject.getExternalTakerFeeRate()).isEqualTo("0.0005");
        assertThat(subject.getExternalMakerFeeRate()).isEqualTo("0.0002");
    }

    @Test
    @DisplayName("U11.25 — ставка комиссии: три подтверждения подряд")
    void u11_25_aSeriesOfConfirmationsAccumulates() {
        TradeFeeRate subject = feeRate();

        subject.confirm(at(1), "VIP1");
        subject.confirm(at(2), "VIP1");
        subject.confirm(at(3), "VIP2");

        assertThat(subject.getRefreshCount()).isEqualTo(3L);
        assertThat(subject.getExternalModifiedAt()).isEqualTo(at(3));
        assertThat(subject.getExternalFeeLevel()).isEqualTo("VIP2");
    }

    @Test
    @DisplayName("U11.26 — контейнер баланса: замена списка валют")
    void u11_26_replacingBalancesDropsThePreviousRows() {
        BalanceContainer subject = new BalanceContainer();
        subject.setExternalUpdatedAt(at(2));
        subject.setBalances(new ArrayList<>(List.of(balance("USDT"))));
        List<Balance> replacement = List.of(balance("BTC"), balance("ETH"));

        subject.replaceBalances(replacement);

        assertThat(subject.getBalances()).isEqualTo(replacement);
        assertThat(subject.getExternalUpdatedAt()).isEqualTo(at(2));
    }

    /** Валидность снимка проверяет вызывающий. */
    @Test
    @DisplayName("U11.27 — контейнер баланса: замена пустым списком")
    void u11_27_replacingWithAnEmptyListIsNotRefused() {
        BalanceContainer subject = new BalanceContainer();
        subject.setBalances(new ArrayList<>(List.of(balance("USDT"))));

        assertThatCode(() -> subject.replaceBalances(List.of())).doesNotThrowAnyException();
        assertThat(subject.getBalances()).isEmpty();
    }

    /**
     * Ошибка идёт тем же требованием причины, что и отмена (пробел `G7`
     * документа, добран под-шагом 3).
     */
    @Test
    @DisplayName("U11.28 — обычная заявка: перевод в ошибочное состояние без причины")
    void u11_28_anOrderErrorRequiresAReason() {
        Order subject = order(1L, Order.Status.ACTIVE, "10", false);

        assertThatThrownBy(() -> subject.toError(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(subject.getStatus()).isEqualTo(Order.Status.ACTIVE);
    }

    /** Тот же write-once на ошибочной тропе (пробел `G7`). */
    @Test
    @DisplayName("U11.29 — обычная заявка: ошибка при уже проставленной причине")
    void u11_29_theOrderErrorReasonIsWriteOnceToo() {
        Order subject = order(1L, Order.Status.ACTIVE, "10", false);
        subject.setCloseReason(Order.CloseReason.CANCELED_BY_STRATEGY);

        subject.toError(Order.CloseReason.UNKNOWN_EXTERNAL_STATUS);

        assertThat(subject.getStatus()).isEqualTo(Order.Status.ERROR);
        assertThat(subject.getCloseReason()).isEqualTo(Order.CloseReason.CANCELED_BY_STRATEGY);
    }

    /**
     * Неотправленная нога, не найденная полным циклом добычи: снята штатно,
     * и встроенная защита уходит с ней из созданного — отдельного ребра
     * через отправленный у неё нет (docs/lifecycles/Order.md §«Неотправленная
     * нога, не найденная добычей»).
     */
    @Test
    @DisplayName("U11.30 — обычная заявка: неотправленная не дошла до площадки")
    void u11_30_anUnsentOrderIsWithdrawnWithItsProtection() {
        AttachedAlgoOrder protection = attached(AttachedAlgoOrder.Status.CREATED, "5", "90");
        Order subject = orderWith(order(1L, Order.Status.CREATED, null, false), protection);

        subject.toNotPlaced();

        assertThat(subject.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(subject.getCloseReason()).isEqualTo(Order.CloseReason.NOT_PLACED);
        assertThat(subject.isLive()).isFalse();
        assertThat(protection.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(protection.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED);
    }

    /** Стоящее намерение снятия не перетирается: причина write-once. */
    @Test
    @DisplayName("U11.31 — обычная заявка: неотправленная при стоящем намерении снятия")
    void u11_31_aStandingIntentSurvivesTheWithdrawal() {
        Order subject = order(1L, Order.Status.CREATED, null, false);
        subject.setCloseReason(Order.CloseReason.KILL_SWITCH);

        subject.toNotPlaced();

        assertThat(subject.getStatus()).isEqualTo(Order.Status.CANCELED);
        assertThat(subject.getCloseReason()).isEqualTo(Order.CloseReason.KILL_SWITCH);
    }

    /** Признак неотправленной — только созданный статус: отправленная несёт биржевой идентификатор. */
    @Test
    @DisplayName("U11.32 — обычная заявка: признак неотправленной")
    void u11_32_onlyACreatedOrderIsNotSubmitted() {
        assertThat(EnumSet.allOf(Order.Status.class).stream()
                .filter(status -> order(1L, status, null, false).isNotSubmitted()))
                .containsExactly(Order.Status.CREATED);
    }

    /** Неотправленная условная заявка снимается ребром из созданного, минуя отправленный. */
    @Test
    @DisplayName("U11.33 — условная заявка: неотправленная не дошла до площадки")
    void u11_33_anUnsentAlgoOrderIsWithdrawn() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.CREATED, "10", "90");

        subject.toNotPlaced();

        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.CANCELED);
        assertThat(subject.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.NOT_PLACED);
        assertThat(subject.isLive()).isFalse();
    }

    /** Отправленная условная заявка этим ребром не снимается: её ненайденность — пропажа. */
    @Test
    @DisplayName("U11.34 — условная заявка: признак неотправленной")
    void u11_34_onlyACreatedAlgoOrderIsNotSubmitted() {
        assertThat(EnumSet.allOf(AlgoOrder.Status.class).stream()
                .filter(status -> standaloneStop(1L, status, "10", "90").isNotSubmitted()))
                .containsExactly(AlgoOrder.Status.CREATED);
    }

    private static Stream<Arguments> allowedAlgoEdges() {
        return ALGO_MATRIX.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(to -> Arguments.of(entry.getKey(), to)));
    }

    private static Stream<Arguments> forbiddenAlgoEdges() {
        return EnumSet.allOf(AlgoOrder.Status.class).stream()
                .flatMap(from -> transitionTargets().stream()
                        .filter(to -> !ALGO_MATRIX.getOrDefault(from,
                                EnumSet.noneOf(AlgoOrder.Status.class)).contains(to))
                        .map(to -> Arguments.of(from, to)));
    }

    private static Stream<Arguments> allowedAttachedEdges() {
        return ATTACHED_MATRIX.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(to -> Arguments.of(entry.getKey(), to)));
    }

    /** Целевые статусы, у которых есть публичный переводящий метод. */
    private static Set<AlgoOrder.Status> transitionTargets() {
        return EnumSet.of(AlgoOrder.Status.PENDING, AlgoOrder.Status.ACTIVE,
                AlgoOrder.Status.PARTIALLY_COMPLETED, AlgoOrder.Status.COMPLETED,
                AlgoOrder.Status.CANCELED, AlgoOrder.Status.ERROR);
    }

    private static void applyAlgoTransition(AlgoOrder subject, AlgoOrder.Status target) {
        switch (target) {
            case PENDING -> subject.toPending();
            case ACTIVE -> subject.toActive();
            case PARTIALLY_COMPLETED -> subject.toPartiallyComplete();
            case COMPLETED -> subject.toComplete();
            case CANCELED -> subject.toCancel(AlgoOrder.CloseReason.CANCELED_BY_STRATEGY);
            case ERROR -> subject.toError(AlgoOrder.CloseReason.ORDER_FAILED);
            case CREATED -> throw new IllegalArgumentException("переводящего метода в CREATED нет");
        }
    }

    private static void applyAttachedTransition(AttachedAlgoOrder subject, AttachedAlgoOrder.Status target) {
        switch (target) {
            case PENDING -> subject.toPending();
            case ACTIVE -> subject.toActive();
            case COMPLETED -> subject.toComplete();
            case CANCELED -> subject.toCancel(AttachedAlgoOrder.CloseReason.KILL_SWITCH);
            case ERROR -> subject.toError(AttachedAlgoOrder.CloseReason.PROTECTION_PLACEMENT_FAILED);
            case CREATED -> throw new IllegalArgumentException("переводящего метода в CREATED нет");
        }
    }

    private static List<String> publicTransitionNames(Class<?> model) {
        return Stream.of(model.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
    }

    private static Deal dealProvenThrough(java.time.OffsetDateTime moment) {
        Deal deal = new Deal();
        deal.setCoverageProvenThrough(moment);
        return deal;
    }

    private static TradeFeeRate feeRate() {
        TradeFeeRate rate = new TradeFeeRate();
        rate.setExternalTakerFeeRate("0.0005");
        rate.setExternalMakerFeeRate("0.0002");
        return rate;
    }

    private static Balance balance(String currency) {
        Balance balance = new Balance();
        balance.setExternalCurrency(currency);
        balance.setExternalEquity(dec("100"));
        return balance;
    }
}
