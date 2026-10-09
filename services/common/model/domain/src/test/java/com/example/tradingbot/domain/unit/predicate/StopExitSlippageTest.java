package com.example.tradingbot.domain.unit.predicate;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проскок выхода сделки по стопу — по примерам дома формы
 * (docs/spec/stop-exit-slippage.json, величина {@code stopExitSlippage}).
 * Меток клетки не несут — их назначает документ кейсов.
 *
 * <p><b>Базовая сборка:</b> длинная сделка, закрытая стопом, исход штатный,
 * граф полон, эпизод один со средней ценой выхода; транш закрыт стопом, его
 * входная нога налита 100 контрактами по 0.1, встроенная защита сработала на
 * уровне 2910. Налив транша выводится из ног, а не присваивается
 * ({@code Deal#deriveTrancheExposures}).
 */
class StopExitSlippageTest {

    private static final String LEVEL = "2910";

    @Test
    @DisplayName("U25.1 — LONG, гэп через стоп: исполнение на 30 ниже уровня — проскок 300, положителен")
    void aLongGapThroughTheStopIsAdverseAndPositive() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2880", stopTranche(LEVEL, "100"));

        assertThat(deal.stopExitSlippage(true)).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("U25.2 — SHORT: неблагоприятная сторона выше уровня — проскок 60")
    void aShortMeasuresTheAdverseSideAboveTheLevel() {
        Deal deal = stopDeal(StrategyTradeDirection.SHORT, "3096", stopTranche("3090", "100"));

        assertThat(deal.stopExitSlippage(true)).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("U25.3 — LONG, исполнение лучше уровня: проскок отрицателен и нулём не обрезается")
    void aBetterThanLevelExecutionIsNegative() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2912", stopTranche(LEVEL, "100"));

        assertThat(deal.stopExitSlippage(true)).isEqualByComparingTo("-20");
    }

    @Test
    @DisplayName("U25.4 — стоп перенесён отдельной защитой: мерится против сработавшего уровня, снятая встроенная не входит")
    void theMovedStandaloneStopIsTheComparedLevel() {
        DealTranche tranche = stopTranche(LEVEL, "100");
        AttachedAlgoOrder switched = tranche.getOrders().getFirst().getAttachedAlgoOrders().getFirst();
        switched.setStatus(AttachedAlgoOrder.Status.CANCELED);
        switched.setCloseReason(AttachedAlgoOrder.CloseReason.SWITCHED_BY_STRATEGY);
        tranche.getAlgoOrders().add(firedStandalone(AlgoOrder.ConditionType.STOP_LOSS, "3003"));
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2995", tranche);

        assertThat(deal.stopExitSlippage(true)).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("U25.5 — два транша со стопами на одном уровне: экспозиция — сумма наливов, проскок 50")
    void twoTranchesOnOneLevelSumTheirExposure() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2905", stopTranche(LEVEL, "60"),
                stopTranche(LEVEL, "40"));

        assertThat(deal.stopExitSlippage(true)).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("U25.6 — частичный выход reduce-only ногой, остаток добрал стоп: мера пуста")
    void aPartialReduceOnlyExitLeavesTheMeasureEmpty() {
        DealTranche tranche = stopTranche(LEVEL, "100");
        tranche.getOrders().add(leg(true, "40"));
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2980", tranche);

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.7 — соседний налитый транш закрыт не стопом: средняя цена смешана — мера пуста")
    void aNeighbourClosedOtherwiseLeavesTheMeasureEmpty() {
        DealTranche neighbour = stopTranche(LEVEL, "40");
        neighbour.setCloseReason(DealTranche.CloseReason.STRATEGY_EXIT);
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2980", stopTranche(LEVEL, "60"), neighbour);

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.8 — транш без налива с иной причиной меру не портит")
    void anUnfilledTrancheDoesNotSpoilTheMeasure() {
        DealTranche expired = tranche();
        expired.setCloseReason(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        expired.getOrders().add(leg(false, "0"));
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2905", stopTranche(LEVEL, "100"), expired);

        assertThat(deal.stopExitSlippage(true)).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("U25.9 — сработал трейлинг: наблюдённый уровень мерой не берётся — мера пуста")
    void aFiredTrailingLeavesTheMeasureEmpty() {
        DealTranche tranche = tranche();
        tranche.setCloseReason(DealTranche.CloseReason.STOP_LOSS);
        tranche.getOrders().add(leg(false, "100"));
        tranche.getAlgoOrders().add(firedStandalone(AlgoOrder.ConditionType.TRAILING_PERCENTS, "2950"));
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2945", tranche);

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.10 — лестница стопов на разных уровнях: средний уровень не взвешивается — мера пуста")
    void aStopLadderLeavesTheMeasureEmpty() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2895", stopTranche(LEVEL, "60"),
                stopTranche("2900", "40"));

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.11 — аварийный терминал: причина не стоп — мера пуста при любом графе")
    void anEmergencyTerminalLeavesTheMeasureEmpty() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2880", stopTranche(LEVEL, "100"));
        deal.setCloseReason(Deal.CloseReason.EMERGENCY_CLOSE);

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.12 — граф на терминале неполон: мера пуста")
    void anIncompleteGraphLeavesTheMeasureEmpty() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2880", stopTranche(LEVEL, "100"));

        assertThat(deal.stopExitSlippage(false)).isNull();
    }

    @Test
    @DisplayName("U25.13 — ликвидация: исход не штатный — мера пуста")
    void aLiquidationLeavesTheMeasureEmpty() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2850", stopTranche(LEVEL, "100"));
        deal.setCloseOutcome(Deal.CloseOutcome.LIQUIDATION);

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.14 — два эпизода: средняя цена выхода у каждого своя — мера пуста")
    void twoEpisodesLeaveTheMeasureEmpty() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2905", stopTranche(LEVEL, "100"));
        deal.getPositions().add(closedEpisode("2900"));

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.15 — средней цены выхода в записи закрытия нет: мера пуста, а не ноль")
    void anAbsentExitPriceLeavesTheMeasureEmpty() {
        Deal deal = stopDeal(StrategyTradeDirection.LONG, null, stopTranche(LEVEL, "100"));

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    @Test
    @DisplayName("U25.16 — восстановленная сделка без входных ног: вышедшая экспозиция ноль — мера пуста")
    void aRecoveredDealWithoutEntryLegsLeavesTheMeasureEmpty() {
        DealTranche recovered = tranche();
        recovered.setCloseReason(DealTranche.CloseReason.STOP_LOSS);
        recovered.getAlgoOrders().add(firedStandalone(AlgoOrder.ConditionType.STOP_LOSS, LEVEL));
        Deal deal = stopDeal(StrategyTradeDirection.LONG, "2905", recovered);

        assertThat(deal.stopExitSlippage(true)).isNull();
    }

    // --- сборка ------------------------------------------------------------

    /** Сделка, закрытая стопом, со штатным исходом и одним закрытым эпизодом. */
    private static Deal stopDeal(StrategyTradeDirection direction, String exitPrice, DealTranche... tranches) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(Deal.Status.CLOSED);
        deal.setDirection(direction);
        deal.setCloseReason(Deal.CloseReason.STOP_LOSS);
        deal.setCloseOutcome(Deal.CloseOutcome.NORMAL_EXIT);
        deal.setTranches(new ArrayList<>(List.of(tranches)));
        deal.setPositions(new ArrayList<>(List.of(closedEpisode(exitPrice))));
        deal.deriveTrancheExposures();
        return deal;
    }

    /** Транш, закрытый стопом: налитая входная нога и сработавшая встроенная защита. */
    private static DealTranche stopTranche(String level, String fill) {
        DealTranche tranche = tranche();
        tranche.setCloseReason(DealTranche.CloseReason.STOP_LOSS);
        Order entry = leg(false, fill);
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setStatus(AttachedAlgoOrder.Status.COMPLETED);
        protection.setCloseReason(AttachedAlgoOrder.CloseReason.TRIGGERED);
        protection.setSize(new BigDecimal(fill));
        protection.setStopLossTriggerPrice(new BigDecimal(level));
        entry.getAttachedAlgoOrders().add(protection);
        tranche.getOrders().add(entry);
        return tranche;
    }

    private static DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setStatus(DealTranche.Status.CLOSED);
        tranche.setOrders(new ArrayList<>());
        tranche.setAlgoOrders(new ArrayList<>());
        return tranche;
    }

    /** Налитая нога: входная либо собственная reduce-only, стоимость контракта 0.1. */
    private static Order leg(Boolean reducingOnly, String fill) {
        Order order = new Order();
        order.setStatus(Order.Status.COMPLETED);
        order.setPositionReducingOnly(reducingOnly);
        order.setAccumulatedFillSize(new BigDecimal(fill));
        order.setPlannedContractValue(new BigDecimal("0.1"));
        order.setAttachedAlgoOrders(new ArrayList<>());
        return order;
    }

    /** Отдельная защита, сработавшая у площадки, с объявленным либо наблюдённым уровнем. */
    private static AlgoOrder firedStandalone(AlgoOrder.ConditionType type, String level) {
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setType(AlgoOrder.TriggerPriceType.MARK);
        stopLoss.setValue(new BigDecimal(level));
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setStatus(AlgoOrder.Status.COMPLETED);
        algoOrder.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        algoOrder.setConditionType(type);
        algoOrder.setSize(new BigDecimal("100"));
        algoOrder.setCondition(new Condition(type, new Trigger(stopLoss, null), null));
        return algoOrder;
    }

    /** Закрытый эпизод с добытой записью закрытия и средней ценой выхода. */
    private static Position closedEpisode(String exitPrice) {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        episode.setExternalSize(BigDecimal.ZERO);
        episode.setExternalCloseType("2");
        episode.setExternalCloseAveragePrice(isNull(exitPrice) ? null : new BigDecimal(exitPrice));
        return episode;
    }
}
