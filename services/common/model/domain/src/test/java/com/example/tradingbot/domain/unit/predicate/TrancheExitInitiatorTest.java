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
import java.time.Instant;
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
 * reduce-only ног — статус и причина налива; у отдельных защит, где клетка
 * их сравнивает, — момент срабатывания на площадке и ключ строки.
 *
 * <p><b>Ожидания всех клеток группы — из дома</b>
 * (docs/lifecycles/DealTranche.md §«Инициатор выхода транша читается по
 * его фактам»): перевод типа условия, порядок носителей, двусторонняя пара
 * (`U5.9`), пустой тип (`U5.10`) и выбор последней сработавшей среди
 * нескольких отдельных (`U5.11`-`U5.13`). Звено `Z3` остаётся исполнителем,
 * а не источником ожидания.
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

    /** Какая нога пары исполнилась, тип условия не говорит — ответ родовой. */
    @Test
    @DisplayName("U5.9 — сработавшая отдельная защита с типом двусторонней пары")
    void u5_9_aTriggeredPairNamesStrategyExit() {
        assertThat(withTriggeredStandalone(AlgoOrder.ConditionType.OCO_FULL).exitInitiatedReason())
                .isEqualTo(DealTranche.CloseReason.STRATEGY_EXIT);
    }

    /**
     * Защита без типа не называется инициатором и чтения не обрывает: ответ
     * даёт следующий носитель — здесь налитая собственная reduce-only нога.
     */
    @Test
    @DisplayName("U5.10 — у отдельной защиты тип условия пуст")
    void u5_10_anUntypedTriggeredProtectionIsSkipped() {
        AlgoOrder untyped = triggeredStandalone(1L, null, null);
        DealTranche subject = trancheOf(trancheWithExposure("10"),
                List.of(filledReduceOnly()), List.of(untyped));

        assertThat(subject.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.STRATEGY_EXIT);
    }

    /**
     * Частичный тейк сработал раньше, стоп — позже и добрал остаток: ответ
     * даёт стоп в ОБОИХ порядках коллекции — выбирает модель, а не порядок,
     * в котором сборщик графа разложил строки.
     */
    @Test
    @DisplayName("U5.11 — две сработавшие отдельные защиты, моменты срабатывания различны")
    void u5_11_theLastTriggeredAnswersWhateverTheCollectionOrder() {
        AlgoOrder earlierTake = triggeredStandalone(2L, AlgoOrder.ConditionType.PARTIAL_TAKE_PROFIT,
                Instant.parse("2026-09-19T10:00:00Z"));
        AlgoOrder laterStop = triggeredStandalone(1L, AlgoOrder.ConditionType.STOP_LOSS,
                Instant.parse("2026-09-19T10:05:00Z"));

        DealTranche earlierFirst = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(earlierTake, laterStop));
        DealTranche laterFirst = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(laterStop, earlierTake));

        assertThat(earlierFirst.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.STOP_LOSS);
        assertThat(laterFirst.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.STOP_LOSS);
    }

    /** Защита без наблюдённого момента уступает защите с моментом, даже заведённая позже. */
    @Test
    @DisplayName("U5.12 — у одной из двух сработавших защит момент срабатывания не наблюдён")
    void u5_12_anObservedMomentOutranksAnAbsentOne() {
        AlgoOrder observedTake = triggeredStandalone(1L, AlgoOrder.ConditionType.TAKE_PROFIT,
                Instant.parse("2026-09-19T10:00:00Z"));
        AlgoOrder unobservedStop = triggeredStandalone(2L, AlgoOrder.ConditionType.STOP_LOSS, null);
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(unobservedStop, observedTake));

        assertThat(subject.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.TAKE_PROFIT);
    }

    /** Без моментов у обеих отвечает заведённая позже — больший ключ строки. */
    @Test
    @DisplayName("U5.13 — у обеих сработавших защит момент срабатывания не наблюдён")
    void u5_13_withoutMomentsTheLaterCreatedAnswers() {
        AlgoOrder olderStop = triggeredStandalone(1L, AlgoOrder.ConditionType.STOP_LOSS, null);
        AlgoOrder newerTake = triggeredStandalone(2L, AlgoOrder.ConditionType.TAKE_PROFIT, null);
        DealTranche subject = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(olderStop, newerTake));

        assertThat(subject.exitInitiatedReason()).isEqualTo(DealTranche.CloseReason.TAKE_PROFIT);
    }

    private static DealTranche withTriggeredStandalone(AlgoOrder.ConditionType conditionType) {
        AlgoOrder protection = triggeredStandalone(1L, conditionType, null);
        return trancheOf(trancheWithExposure("10"), List.of(), List.of(protection));
    }

    private static AlgoOrder triggeredStandalone(Long id, AlgoOrder.ConditionType conditionType,
                                                 Instant triggeredAt) {
        AlgoOrder protection = standaloneStop(id, AlgoOrder.Status.COMPLETED, "10", "90");
        protection.setConditionType(conditionType);
        protection.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        protection.setExternalTriggerTime(triggeredAt);
        return protection;
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
