package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.attached;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.order;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.orderWith;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Инициатор выхода транша — группа `U5` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/lifecycles/DealTranche.md §«Писатель причины закрытия транша —
 * обработчик терминального ребра»).
 *
 * <p><b>Базовая сборка:</b> транш с коллекциями заявок и отдельных
 * условных заявок; у защит проставлены причины закрытия и типы условия; у
 * reduce-only ног — статус и причина налива.
 *
 * <p><b>Две клетки группы не прогоняются, и это не пропуск.</b> `U5.9`
 * (двусторонняя пара) и `U5.10` (пустой тип условия) ожидания не имеют:
 * дом перевода типа сработавшей защиты в причину выхода молчит, а
 * отображение и названное ограничение живут только в javadoc реализации
 * (находка `D-4`, звено `Z3`). Ожидание, взятое из реализации, ожиданием
 * не является.
 */
class TrancheExitInitiatorTest {

    @Test
    @DisplayName("U5.1 — отдельная защита сработала, тип условия — остановка убытка")
    void u5_1_aTriggeredStopNamesStopLoss() {
        assertThat(withTriggeredStandalone(AlgoOrder.ConditionType.STOP_LOSS).exitInitiatedReason())
                .isEqualTo(DealTranche.CloseReason.STOP_LOSS);
    }

    @Test
    @DisplayName("U5.2 — отдельная защита сработала, тип условия — тейк-профит")
    void u5_2_aTriggeredTakeProfitNamesTakeProfit() {
        assertThat(withTriggeredStandalone(AlgoOrder.ConditionType.TAKE_PROFIT).exitInitiatedReason())
                .isEqualTo(DealTranche.CloseReason.TAKE_PROFIT);
    }

    /** Тип у встроенной защиты один — остановка убытка. */
    @Test
    @DisplayName("U5.3 — сработала встроенная защита, отдельных нет")
    void u5_3_aTriggeredAttachedNamesStopLoss() {
        DealTranche subject = trancheOf(trancheWithExposure("10"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false),
                        triggeredAttached())),
                List.of());

        assertThat(subject.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.STOP_LOSS);
    }

    /** Порядок чтения на встроенную не откатывается. */
    @Test
    @DisplayName("U5.4 — сработала и отдельная, и встроенная")
    void u5_4_theStandaloneAnswersFirst() {
        AlgoOrder standalone = standaloneStop(2L, AlgoOrder.Status.COMPLETED, "10", "90");
        standalone.setConditionType(AlgoOrder.ConditionType.TAKE_PROFIT);
        standalone.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        DealTranche subject = trancheOf(trancheWithExposure("10"),
                List.of(orderWith(order(1L, Order.Status.COMPLETED, "10", false), triggeredAttached())),
                List.of(standalone));

        assertThat(subject.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.TAKE_PROFIT);
    }

    @Test
    @DisplayName("U5.5 — защиты не срабатывали, налитая собственная reduce-only нога есть")
    void u5_5_aFilledOwnExitNamesStrategyExit() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(filledReduceOnly()), List.of());

        assertThat(subject.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.STRATEGY_EXIT);
    }

    /** Читатель переводит пустоту в закрытие вне нашего ведения. */
    @Test
    @DisplayName("U5.6 — reduce-only нога есть, но не налита")
    void u5_6_anUnfilledOwnExitLeavesTheInitiatorEmpty() {
        DealTranche subject = trancheOf(trancheWithExposure(null),
                List.of(order(1L, Order.Status.CANCELED, null, true)), List.of());

        assertThat(subject.exitInitiatedReason()).isNull();
    }

    @Test
    @DisplayName("U5.7 — ни защит, ни reduce-only ног")
    void u5_7_nothingNamesAnInitiator() {
        DealTranche subject = trancheOf(trancheWithExposure(null), List.of(), List.of());

        assertThat(subject.exitInitiatedReason()).isNull();
    }

    /** Трейлинг относится к тому же исходу, что и остановка убытка. */
    @Test
    @DisplayName("U5.8 — сработавшая отдельная защита с типом трейлинга")
    void u5_8_aTriggeredTrailingNamesStopLoss() {
        assertThat(withTriggeredStandalone(AlgoOrder.ConditionType.TRAILING_PERCENTS).exitInitiatedReason())
                .isEqualTo(DealTranche.CloseReason.STOP_LOSS);
    }

    private static DealTranche withTriggeredStandalone(AlgoOrder.ConditionType conditionType) {
        AlgoOrder protection = standaloneStop(1L, AlgoOrder.Status.COMPLETED, "10", "90");
        protection.setConditionType(conditionType);
        protection.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        return trancheOf(trancheWithExposure("10"), List.of(), List.of(protection));
    }

    private static AttachedAlgoOrder triggeredAttached() {
        AttachedAlgoOrder protection = attached(AttachedAlgoOrder.Status.COMPLETED, "10", "90");
        protection.setCloseReason(AttachedAlgoOrder.CloseReason.TRIGGERED);
        return protection;
    }

    private static Order filledReduceOnly() {
        Order exit = order(1L, Order.Status.COMPLETED, "10", true);
        exit.setCloseReason(Order.CloseReason.FILLED);
        return exit;
    }
}
