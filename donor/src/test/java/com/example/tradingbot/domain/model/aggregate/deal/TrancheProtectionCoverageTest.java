package com.example.tradingbot.domain.model.aggregate.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает покрытие защитой транша с его домом
 * (docs/rules/live-risk-protection.md; форма —
 * docs/spec/protection-coverage.json, величины trancheCoverage,
 * coverageAfterRemoval, removalAllowed).
 *
 * <p>Несущее: защитой считается только живая заявка с ДЕЙСТВУЮЩИМ уровнем
 * остановки убытка. Тейк уровня не несёт никогда, трейлинг — до первого
 * наблюдения; считать их защитой значило бы разрешить снятие прежней
 * защиты по размеру при нерезолвимом уровне.
 */
class TrancheProtectionCoverageTest {

    @Test
    @DisplayName("Снятие законно, когда остающееся покрытие не ниже экспозиции транша")
    void removalAllowedWhenRemainingCoverageHoldsExposure() {
        DealTranche tranche = tranche(BigDecimal.TEN);
        tranche.setAlgoOrders(List.of(
                standalone(1L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN),
                standalone(2L, AlgoOrder.ConditionType.TRAILING_PERCENTS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN)));
        observedTrailing(tranche.getAlgoOrders().get(1), new BigDecimal("99"));

        assertTrue(tranche.removalAllowed(1L), "трейлинг с наблюдённым уровнем держит покрытие");
        assertEquals(BigDecimal.TEN, tranche.coverageWithoutAlgoOrder(1L));
    }

    @Test
    @DisplayName("Снятие последней защиты над живой экспозицией не проходит")
    void removalOfLastProtectionRejected() {
        DealTranche tranche = tranche(BigDecimal.TEN);
        tranche.setAlgoOrders(List.of(
                standalone(1L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN)));

        assertFalse(tranche.removalAllowed(1L));
        assertEquals(BigDecimal.ZERO, tranche.coverageWithoutAlgoOrder(1L));
    }

    @Test
    @DisplayName("Трейлинг без наблюдённого уровня защитой не считается — покрытие им не держится")
    void trailingWithoutObservedLevelIsNotProtection() {
        DealTranche tranche = tranche(BigDecimal.TEN);
        tranche.setAlgoOrders(List.of(
                standalone(1L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN),
                standalone(2L, AlgoOrder.ConditionType.TRAILING_PERCENTS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN)));

        assertFalse(tranche.removalAllowed(1L), "снятие стопа оставило бы транш без worst-case выхода");
    }

    @Test
    @DisplayName("Тейк уровня остановки убытка не несёт и в покрытие не входит")
    void takeProfitIsNotCoverage() {
        DealTranche tranche = tranche(BigDecimal.TEN);
        tranche.setAlgoOrders(List.of(
                standalone(1L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN),
                standalone(2L, AlgoOrder.ConditionType.TAKE_PROFIT, AlgoOrder.Status.ACTIVE, BigDecimal.TEN)));

        assertEquals(BigDecimal.ZERO, tranche.coverageWithoutAlgoOrder(1L));
    }

    @Test
    @DisplayName("Локально созданная защита на бирже не стои́т и покрытия не даёт")
    void createdProtectionDoesNotCover() {
        DealTranche tranche = tranche(BigDecimal.TEN);
        tranche.setAlgoOrders(List.of(
                standalone(1L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN),
                standalone(2L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.CREATED, BigDecimal.TEN)));

        assertFalse(tranche.removalAllowed(1L), "заявка CREATED биржей не подтверждена");
    }

    @Test
    @DisplayName("Встроенная защита покрывает не больше налива своей входной заявки")
    void attachedCoverageBoundedByParentFill() {
        DealTranche tranche = tranche(new BigDecimal("4"));
        Order entry = new Order();
        entry.setId(10L);
        entry.setAccumulatedFillSize(new BigDecimal("4"));
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setId(11L);
        protection.setStatus(AttachedAlgoOrder.Status.ACTIVE);
        protection.setSize(BigDecimal.TEN);
        entry.setAttachedAlgoOrders(List.of(protection));
        tranche.setOrders(List.of(entry));
        tranche.setAlgoOrders(List.of(
                standalone(1L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.ACTIVE, new BigDecimal("4"))));

        // Снятие отдельной защиты законно: встроенная покрывает налив целиком.
        assertTrue(tranche.removalAllowed(1L));
        assertEquals(new BigDecimal("4"), tranche.coverageWithoutAlgoOrder(1L));
    }

    @Test
    @DisplayName("Живая защита при нуле предъявленных заявок — предикат отказывает вычислением")
    void unresolvableWhenGraphIncomplete() {
        // Экспозиция считается по заявкам транша и здесь схлопнулась бы в ноль:
        // сравнение с нулём разрешило бы снятие последней защиты над живым риском.
        DealTranche tranche = tranche(BigDecimal.ZERO);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of(
                standalone(1L, AlgoOrder.ConditionType.STOP_LOSS, AlgoOrder.Status.ACTIVE, BigDecimal.TEN)));

        assertNull(tranche.removalAllowed(1L));
    }

    /** Транш с предъявленной входной заявкой: граф полон, предикат вычислим. */
    private DealTranche tranche(BigDecimal entryFilled) {
        DealTranche tranche = new DealTranche();
        tranche.setId(100L);
        tranche.setEpisodeSeq(1);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(entryFilled);
        Order entry = new Order();
        entry.setId(10L);
        entry.setAccumulatedFillSize(entryFilled);
        tranche.setOrders(List.of(entry));
        return tranche;
    }

    private AlgoOrder standalone(Long id, AlgoOrder.ConditionType type, AlgoOrder.Status status, BigDecimal size) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(id);
        algoOrder.setConditionType(type);
        algoOrder.setStatus(status);
        algoOrder.setSize(size);
        return algoOrder;
    }

    private void observedTrailing(AlgoOrder algoOrder, BigDecimal observedPrice) {
        Trailing trailing = new Trailing();
        trailing.setExternalPrice(observedPrice);
        Condition condition = new Condition();
        condition.setType(algoOrder.getConditionType());
        condition.setTrailing(trailing);
        algoOrder.setCondition(condition);
    }
}
