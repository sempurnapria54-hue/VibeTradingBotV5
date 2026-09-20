package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneTrailing;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Действующий уровень остановки убытка транша — группа `U3` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/protection-coverage.json, величины
 * {@code trancheStopCurrent}, {@code protectionStopLevel},
 * {@code dealStopUnresolved}).
 *
 * <p><b>Базовая сборка:</b> транш с экспозицией; набор живых защит с
 * разными уровнями — встроенные на входной заявке и отдельные;
 * направление сделки подаётся аргументом.
 *
 * <p>Наименее благоприятный уровень — тот, до которого цена дойдёт
 * ПОСЛЕДНЕЙ: у длинной позиции самый низкий, у короткой самый высокий.
 */
class TrancheStopLevelTest {

    @Test
    @DisplayName("U3.1 — длинное направление, три живые защиты с разными уровнями")
    void u3_1_longTakesTheLowestLevel() {
        DealTranche subject = threeLevels();

        assertThat(subject.worstActiveStopLevel(StrategyTradeDirection.LONG))
                .isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("U3.2 — короткое направление, те же три")
    void u3_2_shortTakesTheHighestLevel() {
        DealTranche subject = threeLevels();

        assertThat(subject.worstActiveStopLevel(StrategyTradeDirection.SHORT))
                .isEqualByComparingTo("110");
    }

    /** Приоритет носителя занижал бы риск: в сравнение входят обе стороны. */
    @Test
    @DisplayName("U3.3 — уровни есть и у встроенной, и у отдельной защиты")
    void u3_3_theCarrierClassDoesNotNarrowTheComparison() {
        DealTranche attachedLower = trancheOf(trancheWithExposure("10"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false),
                        attached(AttachedAlgoOrder.Status.ACTIVE, "10", "85"))),
                List.of(standaloneStop(2L, AlgoOrder.Status.ACTIVE, "10", "90")));
        DealTranche standaloneLower = trancheOf(trancheWithExposure("10"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false),
                        attached(AttachedAlgoOrder.Status.ACTIVE, "10", "95"))),
                List.of(standaloneStop(2L, AlgoOrder.Status.ACTIVE, "10", "90")));

        assertThat(attachedLower.worstActiveStopLevel(StrategyTradeDirection.LONG))
                .isEqualByComparingTo("85");
        assertThat(standaloneLower.worstActiveStopLevel(StrategyTradeDirection.LONG))
                .isEqualByComparingTo("90");
    }

    /** Защиты нет — пусто, а не ноль. */
    @Test
    @DisplayName("U3.4 — живых защит с уровнем нет вовсе")
    void u3_4_anAbsentLevelIsEmptyNotZero() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(), List.of());

        assertThat(subject.worstActiveStopLevel(StrategyTradeDirection.LONG)).isNull();
    }

    @Test
    @DisplayName("U3.5 — трейлинг без наблюдённой цены при второй защите с уровнем")
    void u3_5_anUnobservedTrailingStaysOutOfTheComparison() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneTrailing(1L, AlgoOrder.Status.ACTIVE, "10", null),
                        standaloneStop(2L, AlgoOrder.Status.ACTIVE, "10", "90")));

        assertThat(subject.worstActiveStopLevel(StrategyTradeDirection.LONG))
                .isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("U3.6 — трейлинг с наблюдённой ценой — единственная защита")
    void u3_6_anObservedTrailingCarriesItsLevel() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneTrailing(1L, AlgoOrder.Status.ACTIVE, "10", "88")));

        assertThat(subject.worstActiveStopLevel(StrategyTradeDirection.LONG))
                .isEqualByComparingTo("88");
    }

    /**
     * Сравнение идёт с КОРОТКИМ направлением, и пустое даёт ветвь
     * минимума. Продуктового ожидания здесь нет: дом направления пустым
     * не допускает (docs/models/domain/aggregate/Deal.md §Структура).
     */
    @Test
    @DisplayName("U3.7 — направление подано пустым")
    void u3_7_anAbsentDirectionFallsToTheMinimumBranch() {
        DealTranche subject = threeLevels();

        assertThat(subject.worstActiveStopLevel(null)).isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("U3.8 — экспозиция положительна, ни одна живая защита уровня не несёт")
    void u3_8_exposureWithoutLevelIsUnresolved() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(), List.of());

        assertThat(subject.stopUnresolved()).isTrue();
    }

    /** Без экспозиции резолвить нечего. */
    @Test
    @DisplayName("U3.9 — экспозиция нулевая, уровня нет")
    void u3_9_noExposureIsNotUnresolved() {
        DealTranche subject = trancheOf(trancheWithExposure(null), List.of(), List.of());

        assertThat(subject.stopUnresolved()).isFalse();
    }

    @Test
    @DisplayName("U3.10 — экспозиция положительна, уровень есть")
    void u3_10_exposureWithLevelIsResolved() {
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90")));

        assertThat(subject.stopUnresolved()).isFalse();
    }

    /** Три живые защиты с уровнями 100, 90 и 110. */
    private static DealTranche threeLevels() {
        return trancheOf(trancheWithExposure("10"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false),
                        attached(AttachedAlgoOrder.Status.ACTIVE, "10", "100"))),
                List.of(standaloneStop(2L, AlgoOrder.Status.ACTIVE, "10", "90"),
                        standaloneStop(3L, AlgoOrder.Status.ACTIVE, "10", "110")));
    }
}
