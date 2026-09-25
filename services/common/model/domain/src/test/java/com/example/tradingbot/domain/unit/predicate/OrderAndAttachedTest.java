package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Заявка и её встроенная защита — группа `U9` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/order-lifecycle.json, {@code orderIsLive},
 * {@code attachedIsActiveLike}; docs/spec/protection-coverage.json,
 * {@code coveredSize} носителя ATTACHED;
 * docs/models/domain/core/Order.md §«Встроенная защита»).
 *
 * <p><b>Базовая сборка:</b> заявка со статусом, причиной финализации и
 * наливом; коллекция встроенных защит — со своими статусами и
 * объявленными размерами.
 */
class OrderAndAttachedTest {

    /** Локально созданная ВХОДИТ в множество живых у обычной заявки. */
    @ParameterizedTest
    @EnumSource(value = Order.Status.class, names = {"CREATED", "PENDING", "ACTIVE", "PARTIALLY_COMPLETED"})
    @DisplayName("U9.1 — каждый из четырёх нетерминальных статусов порознь")
    void u9_1_nonTerminalStatusesAreLive(Order.Status status) {
        assertThat(order(1L, status, null, false).isLive()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Order.Status.class, names = {"COMPLETED", "CANCELED", "ERROR"})
    @DisplayName("U9.2 — каждый из трёх терминальных статусов порознь")
    void u9_2_terminalStatusesAreNotLive(Order.Status status) {
        assertThat(order(1L, status, null, false).isLive()).isFalse();
    }

    @Test
    @DisplayName("U9.3 — статус пуст")
    void u9_3_anAbsentStatusIsNotLive() {
        Order subject = order(1L, null, null, false);

        assertThatCode(subject::isLive).doesNotThrowAnyException();
        assertThat(subject.isLive()).isFalse();
    }

    @Test
    @DisplayName("U9.4 — завершённый статус и причина налива")
    void u9_4_theFilledPairMarksAFilledOrder() {
        assertThat(finalized(Order.Status.COMPLETED, Order.CloseReason.FILLED).isFilled()).isTrue();
    }

    /** Частичный налив финализацией не является. */
    @Test
    @DisplayName("U9.5 — частично исполненный статус")
    void u9_5_partiallyCompletedIsNotFilled() {
        assertThat(finalized(Order.Status.PARTIALLY_COMPLETED, Order.CloseReason.FILLED).isFilled()).isFalse();
    }

    /** Требуется пара «статус, причина». */
    @Test
    @DisplayName("U9.6 — завершённый статус с причиной, отличной от налива")
    void u9_6_completedWithAnotherReasonIsNotFilled() {
        assertThat(finalized(Order.Status.COMPLETED, Order.CloseReason.CANCELED_BY_STRATEGY).isFilled())
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AttachedAlgoOrder.Status.class, names = {"PENDING", "ACTIVE"})
    @DisplayName("U9.7 — встроенная защита в отправленном либо активном статусе")
    void u9_7_anActiveLikeAttachedIsLiveProtection(AttachedAlgoOrder.Status status) {
        Order subject = orderWith(order(1L, Order.Status.ACTIVE, "10", false), attached(status, "5", "90"));

        assertThat(subject.hasActiveAttachedProtection()).isTrue();
    }

    /** Созданная в множество живых у встроенной не входит. */
    @Test
    @DisplayName("U9.8 — встроенная защита в локально созданном статусе")
    void u9_8_aCreatedAttachedIsNotLiveProtection() {
        Order subject = orderWith(order(1L, Order.Status.ACTIVE, "10", false),
                attached(AttachedAlgoOrder.Status.CREATED, "5", "90"));

        assertThat(subject.hasActiveAttachedProtection()).isFalse();
    }

    @Test
    @DisplayName("U9.9 — встроенные защиты все терминальны")
    void u9_9_terminalAttachedIsNotLiveProtection() {
        Order subject = orderWith(order(1L, Order.Status.ACTIVE, "10", false),
                attached(AttachedAlgoOrder.Status.COMPLETED, "5", "90"),
                attached(AttachedAlgoOrder.Status.CANCELED, "5", "90"));

        assertThat(subject.hasActiveAttachedProtection()).isFalse();
    }

    @Test
    @DisplayName("U9.10 — коллекция встроенных защит пуста")
    void u9_10_anAbsentAttachedCollectionIsNotLiveProtection() {
        Order subject = order(1L, Order.Status.ACTIVE, "10", false);

        assertThatCode(subject::hasActiveAttachedProtection).doesNotThrowAnyException();
        assertThat(subject.hasActiveAttachedProtection()).isFalse();
    }

    @Test
    @DisplayName("U9.11 — объявленный размер защиты меньше налива родителя")
    void u9_11_theDeclaredSizeCovers() {
        assertThat(attached(AttachedAlgoOrder.Status.ACTIVE, "3", "90").coveredSize(dec("10")))
                .isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("U9.12 — объявленный размер больше налива родителя")
    void u9_12_theParentFillCaps() {
        assertThat(attached(AttachedAlgoOrder.Status.ACTIVE, "10", "90").coveredSize(dec("4")))
                .isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("U9.13 — налив родителя пуст")
    void u9_13_anAbsentParentFillReadsAsZero() {
        assertThat(attached(AttachedAlgoOrder.Status.ACTIVE, "10", "90").coveredSize(null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("U9.14 — объявленный размер защиты пуст")
    void u9_14_anAbsentDeclaredSizeReadsAsZero() {
        assertThat(attached(AttachedAlgoOrder.Status.ACTIVE, null, "90").coveredSize(dec("10")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Выражение носителя ATTACHED есть минимум двух слагаемых и отсечки не несёт. */
    @Test
    @DisplayName("U9.15 — налив родителя отрицателен")
    void u9_15_aNegativeParentFillIsNotClamped() {
        assertThat(attached(AttachedAlgoOrder.Status.ACTIVE, "5", "90").coveredSize(dec("-2")))
                .isEqualByComparingTo("-2");
    }

    @Test
    @DisplayName("U9.16 — нога налита целиком: налив окончателен")
    void u9_16_aFilledLegHasAFinalFill() {
        assertThat(finalized(Order.Status.COMPLETED, Order.CloseReason.FILLED).hasFinalFill()).isTrue();
    }

    /** Финализирует терминал, а не полнота налива. */
    @Test
    @DisplayName("U9.17 — нога снята после частичного налива: налив окончателен")
    void u9_17_aLegCancelledAfterAPartialFillHasAFinalFill() {
        Order subject = order(1L, Order.Status.CANCELED, "2", false);
        subject.setCloseReason(Order.CloseReason.CANCELED_BY_STRATEGY);

        assertThat(subject.hasFinalFill()).isTrue();
    }

    /** Недобытый налив нулём не подменяется и финализацией не читается. */
    @Test
    @DisplayName("U9.18 — нога снята, налив нулевой либо не добыт: налива нет")
    void u9_18_aCancelledLegWithoutAKnownFillHasNoFinalFill() {
        Order empty = order(1L, Order.Status.CANCELED, "0", false);
        Order unknown = order(2L, Order.Status.CANCELED, null, false);

        assertThat(empty.hasFinalFill()).isFalse();
        assertThat(unknown.hasFinalFill()).isFalse();
    }

    /** Живая частично налитая нога ещё меняет экспозицию. */
    @Test
    @DisplayName("U9.19 — нога жива и налита частично: налив не окончателен")
    void u9_19_aLivePartiallyFilledLegHasNoFinalFill() {
        assertThat(order(1L, Order.Status.PARTIALLY_COMPLETED, "2", false).hasFinalFill()).isFalse();
    }

    private static Order finalized(Order.Status status, Order.CloseReason reason) {
        Order subject = order(1L, status, "10", false);
        subject.setCloseReason(reason);
        return subject;
    }
}
