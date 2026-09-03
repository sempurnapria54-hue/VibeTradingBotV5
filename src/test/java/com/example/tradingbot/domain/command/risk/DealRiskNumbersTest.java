package com.example.tradingbot.domain.command.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает четвёрку чисел риска сделки с её исполнимой формой
 * (docs/spec/deal-risk-numbers.json).
 *
 * <p>Несущее — <b>ось эпизода</b>: без неё ноги закрытых эпизодов
 * неотличимы от ног текущего, и пара «взятое ↔ снятое защитой» считалась
 * бы по всей истории сделки, то есть кратно завышенной. И <b>различие
 * заявленного и взятого проходит по ДОЛЕ, а не по множеству ног</b>:
 * снятая с филлом нога входит в оба числа, только в заявленное — своей
 * налитой долей.
 */
class DealRiskNumbersTest {

    @Test
    @DisplayName("Перевыставление входа: снятая нога отдаёт налитую долю, а не всю заявку и не ноль")
    void cancelledLegContributesFilledShare() {
        Deal deal = deal(position(1L, BigDecimal.valueOf(30)),
                leg(Order.Status.CANCELED, 100, 100, 30, 1L),
                leg(Order.Status.ACTIVE, 70, 70, 0, null));

        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);

        // Снятая нога отдаёт в знаменатель налитые 30 (не всю заявку 100 и
        // не ноль), живая — свои 70 целиком: перевыставление входа
        // знаменатель не раздувает и не обнуляет.
        assertEquals(0, numbers.getPlannedRiskAmount().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, numbers.getIncurredRiskAmount().compareTo(BigDecimal.valueOf(30)));
    }

    @Test
    @DisplayName("Живая нога входит заявленным риском ЦЕЛИКОМ, взятым — налитой долей")
    void liveLegSplitsPlannedAndIncurred() {
        Deal deal = deal(position(1L, BigDecimal.valueOf(30)),
                leg(Order.Status.PARTIALLY_COMPLETED, 100, 100, 30, 1L));

        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);

        assertEquals(0, numbers.getPlannedRiskAmount().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, numbers.getIncurredRiskAmount().compareTo(BigDecimal.valueOf(30)));
    }

    @Test
    @DisplayName("Частичный выход уменьшает неотработанную долю, взятый риск не трогает")
    void partialExitShrinksCurrentRiskOnly() {
        Deal deal = deal(position(1L, BigDecimal.valueOf(40)),
                leg(Order.Status.COMPLETED, 100, 100, 100, 1L));

        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);

        assertEquals(0, numbers.getIncurredRiskAmount().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, numbers.getCurrentRiskAmount().compareTo(BigDecimal.valueOf(40)));
    }

    @Test
    @DisplayName("Многоэпизодная сделка: пара считается по ногам ЖИВОГО эпизода, ноги закрытого не участвуют")
    void multiEpisodeDealCountsLiveEpisodeOnly() {
        Deal deal = deal(position(2L, BigDecimal.valueOf(50)),
                leg(Order.Status.COMPLETED, 100, 100, 100, 1L),
                leg(Order.Status.COMPLETED, 50, 50, 50, 2L));

        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);

        assertEquals(0, numbers.getIncurredRiskAmount().compareTo(BigDecimal.valueOf(150)),
                "взятый риск пожизненный: ноги обоих эпизодов");
        assertEquals(0, numbers.getCurrentRiskAmount().compareTo(BigDecimal.valueOf(50)),
                "неотработанная доля — только живой эпизод");
    }

    @Test
    @DisplayName("Нога с пустой осью эпизода в пару не входит")
    void legWithoutEpisodeAxisIsOutOfThePair() {
        Deal deal = deal(position(1L, BigDecimal.valueOf(100)),
                leg(Order.Status.ACTIVE, 100, 100, 100, null));

        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);

        assertEquals(0, numbers.getCurrentRiskAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, numbers.getProtectionRelievedRiskAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Вырожденный размер ноги даёт ноль, а не деление")
    void degenerateLegGivesZero() {
        Deal deal = deal(position(1L, BigDecimal.ZERO), leg(Order.Status.ACTIVE, 0, 0, 0, 1L));

        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);

        assertEquals(0, numbers.getIncurredRiskAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, numbers.getCurrentRiskAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Стоп за безубытком: снятое защитой больше взятого — знак не клэмпится")
    void stopBeyondBreakevenRelievesMoreThanTaken() {
        Deal deal = deal(position(1L, BigDecimal.valueOf(100)),
                leg(Order.Status.COMPLETED, 100, 100, 100, 1L));
        // Защита стои́т ВЫШЕ якоря входа: убыток на ней отрицателен.
        deal.getTranches().getFirst().setAlgoOrders(List.of(stopAt(new BigDecimal("3010"))));

        DealRiskNumbers.Numbers numbers = DealRiskNumbers.compute(deal);

        assertTrue(numbers.getProtectionRelievedRiskAmount().compareTo(BigDecimal.valueOf(100)) > 0,
                "снятое защитой больше взятого, и это факт, а не ошибка");
    }

    @Test
    @DisplayName("Плановый риск ноги — закрытая форма: дистанция плюс round-trip комиссия")
    void plannedRiskIsTheClosedForm() {
        BigDecimal risk = DealRiskNumbers.plannedRisk(StrategyTradeDirection.LONG,
                new BigDecimal("3000"), new BigDecimal("2910"), new BigDecimal("0.0005"),
                BigDecimal.valueOf(100), new BigDecimal("0.1"));

        // (3000 - 2910) + 0.0005 × (3000 + 2910) = 90 + 2.955 = 92.955 на единицу.
        assertEquals(0, risk.compareTo(new BigDecimal("929.5500")), "риск = 92.955 × 100 × 0.1");
    }

    private Deal deal(Position live, Order... legs) {
        DealTranche tranche = new DealTranche();
        tranche.setId(1L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setOrders(new ArrayList<>(List.of(legs)));
        tranche.setAlgoOrders(List.of());

        Deal deal = new Deal();
        deal.setId(1L);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setOrders(new ArrayList<>(List.of(legs)));
        deal.setTranches(new ArrayList<>(List.of(tranche)));
        deal.setPositions(List.of(live));
        return deal;
    }

    private Position position(Long id, BigDecimal size) {
        Position position = new Position();
        position.setId(id);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(size);
        return position;
    }

    /** Нога со своим плановым снимком: цены такие, что риск численно равен plannedRisk. */
    private Order leg(Order.Status status, int plannedRisk, int plannedSize, int filled, Long positionId) {
        Order order = new Order();
        order.setId((long) (plannedRisk * 1000 + plannedSize));
        order.setDealTrancheId(1L);
        order.setType(Order.Type.ENTRY);
        order.setStatus(status);
        order.setAccumulatedFillSize(BigDecimal.valueOf(filled));
        order.setPositionId(positionId);
        order.setPlannedRiskAmount(BigDecimal.valueOf(plannedRisk));
        order.setPlannedSizeContracts(BigDecimal.valueOf(plannedSize));
        order.setPlannedEntryPrice(new BigDecimal("3000"));
        order.setPlannedStopPrice(new BigDecimal("2910"));
        order.setPlannedContractValue(new BigDecimal("0.1"));
        return order;
    }

    private AlgoOrder stopAt(BigDecimal level) {
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setValue(level);
        Trigger trigger = new Trigger();
        trigger.setStopLoss(stopLoss);
        Condition condition = new Condition();
        condition.setTrigger(trigger);

        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setCondition(condition);
        return algoOrder;
    }
}
