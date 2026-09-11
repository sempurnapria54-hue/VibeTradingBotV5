package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.DealRiskNumbers;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.persistence.service.DealDataService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Четвёрка чисел риска сделки — исполнимая форма
 * docs/spec/deal-risk-numbers.json.
 *
 * <p><b>Состояние собирается настоящими полями графа</b> — транши, их ноги
 * и защиты, эпизоды, — а не подменёнными предикатами: подменённый предикат
 * проверял бы модель, которой в проде не существует
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»). Действующий
 * уровень защиты поэтому не подставляется числом, а вытекает из живой
 * защиты транша.
 */
class DealRiskNumbersTest {

    private static final BigDecimal ENTRY = new BigDecimal("3000");
    private static final BigDecimal PLANNED_STOP = new BigDecimal("2910");
    private static final BigDecimal CONTRACT_VALUE = new BigDecimal("0.1");

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealRiskNumbersService service = new DealRiskNumbersService(dealDataService);

    /**
     * Перевыставление входа: снятая нога отдаёт в знаменатель НАЛИТУЮ
     * долю — не всю заявку и не ноль. Довод «снятая заявка не стояла»
     * верен для её неисполненной доли и неверен для налитой.
     */
    @Test
    void canceledLegContributesItsFilledShareToPlannedRisk() {
        Order canceled = entryLeg(Order.Status.CANCELED, "100", "100", "30", 1L);
        Order active = entryLeg(Order.Status.ACTIVE, "70", "70", "0", null);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "30"),
                tranche(List.of(canceled, active), List.of(protection("2910", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getPlannedRiskAmount()).isEqualByComparingTo("100");
        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("30");
    }

    /**
     * Нога в ошибке: заявленное сверх филла из знаменателя выходит,
     * налитое остаётся и там, и во взятом риске.
     */
    @Test
    void erroredLegKeepsOnlyItsFilledShare() {
        Order errored = entryLeg(Order.Status.ERROR, "100", "100", "20", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "20"),
                tranche(List.of(errored), List.of(protection("2910", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getPlannedRiskAmount()).isEqualByComparingTo("20");
        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("20");
    }

    /**
     * Недвинутый стоп: снятое защитой равно нулю АЛГЕБРАИЧЕСКИ — ставка
     * ноги восстановлена обращением сайзинга, и риск при действующем
     * стопе совпал со взятым без подстановки нуля.
     */
    @Test
    void unmovedStopRelievesNothing() {
        Order filled = entryLeg(Order.Status.COMPLETED, "929.55", "100", "100", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "100"),
                tranche(List.of(filled), List.of(protection("2910", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("929.55");
        assertThat(numbers.getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
    }

    /**
     * Стоп за безубытком: снятое защитой БОЛЬШЕ взятого, и знак не
     * клэмпится — отрицательный риск при действующем стопе есть факт, а
     * не ошибка.
     */
    @Test
    void stopBeyondBreakEvenRelievesMoreThanTaken() {
        Order filled = entryLeg(Order.Status.COMPLETED, "929.55", "100", "100", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "100"),
                tranche(List.of(filled), List.of(protection("3010", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getProtectionRelievedRiskAmount()).isEqualByComparingTo("999.5");
    }

    /**
     * Многоэпизодная сделка: пара «взятое ↔ снятое» считается по ногам
     * ЖИВОГО эпизода; ноги закрытого в неё не входят, иначе величина
     * росла бы кратно числу закрытых эпизодов.
     */
    @Test
    void closedEpisodeLegsStayOutOfThePair() {
        Order closedEpisode = entryLeg(Order.Status.COMPLETED, "929.55", "100", "100", 1L);
        Order liveEpisode = entryLeg(Order.Status.COMPLETED, "464.775", "50", "50", 2L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(2L, "50"),
                tranche(List.of(closedEpisode, liveEpisode), List.of(protection("2910", "150"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("1394.325");
        assertThat(numbers.getCurrentRiskAmount()).isEqualByComparingTo("464.775");
    }

    /** Нога с пустым идентификатором эпизода в пару не входит. */
    @Test
    void legWithoutEpisodeAxisStaysOutOfThePair() {
        Order noAxis = entryLeg(Order.Status.ACTIVE, "929.55", "100", "100", null);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "100"),
                tranche(List.of(noAxis), List.of(protection("2910", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
    }

    /** Частичный выход уменьшает неотработанную долю и не трогает взятый риск. */
    @Test
    void partialExitShrinksOnlyTheUnworkedShare() {
        Order filled = entryLeg(Order.Status.COMPLETED, "100", "100", "100", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "40"),
                tranche(List.of(filled), List.of(protection("2910", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("100");
        assertThat(numbers.getCurrentRiskAmount()).isEqualByComparingTo("40");
    }

    /** Вырожденный размер ноги даёт ноль, а не деление. */
    @Test
    void degenerateLegSizeYieldsZeroNotDivision() {
        Order degenerate = entryLeg(Order.Status.ACTIVE, "0", "0", "0", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "0"),
                tranche(List.of(degenerate), List.of(protection("2910", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("0");
        assertThat(numbers.getCurrentRiskAmount()).isEqualByComparingTo("0");
    }

    /**
     * Ставка ноги восстанавливается ОБРАЩЕНИЕМ сайзинга: при вдвое
     * большей ставке те же размер и цены требуют большего заявленного
     * риска, и риск при недвинутом стопе равен ему. Отдельного поля
     * ставки для этого не заводится.
     */
    @Test
    void feeRateIsRecoveredByInvertingTheSizingForm() {
        Order filled = entryLeg(Order.Status.COMPLETED, "959.1", "100", "100", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "100"),
                tranche(List.of(filled), List.of(protection("2910", "100"))));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
    }

    /**
     * Ноги берутся ОБХОДОМ ТРАНШЕЙ: донорское поле агрегата ядром не
     * читается, и нога, лежащая только в нём, в числа не входит
     * (.claude/work/backlog.md §«Донорские поля агрегата сделки в общей
     * библиотеке»).
     */
    @Test
    void legsComeFromTranchesNotFromTheAggregateField() {
        Order onlyInAggregate = entryLeg(Order.Status.COMPLETED, "500", "100", "100", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "100"),
                tranche(List.of(), List.of()));
        deal.setOrders(List.of(onlyInAggregate));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getPlannedRiskAmount()).isEqualByComparingTo("0");
    }

    /**
     * Неполный граф пересчёт запрещает: прежние значения остаются
     * нетронутыми, сделка не сохраняется, звено писателя не завершается.
     * Заниженный заявленный риск ослабил бы кумулятивный потолок — ошибка
     * в разрешающую сторону.
     */
    @Test
    void incompleteGraphLeavesTheNumbersUntouched() {
        Order filled = entryLeg(Order.Status.COMPLETED, "929.55", "100", "100", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "100"),
                tranche(List.of(filled), List.of(protection("2910", "100"))));
        deal.setPlannedRiskAmount(new BigDecimal("777"));
        DealContext context = DealContext.builder().deal(deal).graphComplete(false).build();

        assertThat(service.recompute(context)).isFalse();

        assertThat(deal.getPlannedRiskAmount()).isEqualByComparingTo("777");
        verify(dealDataService, never()).applyRiskNumbers(any());
    }

    /** Полный граф: четвёрка переписывается целиком и уезжает в базу одной записью. */
    @Test
    void completeGraphRewritesAllFourNumbers() {
        Order filled = entryLeg(Order.Status.COMPLETED, "929.55", "100", "100", 1L);
        Deal deal = deal(StrategyTradeDirection.LONG, livePosition(1L, "100"),
                tranche(List.of(filled), List.of(protection("3010", "100"))));
        DealContext context = DealContext.builder().deal(deal).graphComplete(true).build();

        assertThat(service.recompute(context)).isTrue();

        assertThat(deal.getPlannedRiskAmount()).isEqualByComparingTo("929.55");
        assertThat(deal.getIncurredRiskAmount()).isEqualByComparingTo("929.55");
        assertThat(deal.getCurrentRiskAmount()).isEqualByComparingTo("929.55");
        assertThat(deal.getProtectionRelievedRiskAmount()).isEqualByComparingTo("999.5");
        verify(dealDataService).applyRiskNumbers(deal);
    }

    private static Deal deal(StrategyTradeDirection direction, Position live, DealTranche tranche) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setDirection(direction);
        deal.setPositions(List.of(live));
        deal.setTranches(List.of(tranche));
        return deal;
    }

    private static DealTranche tranche(List<Order> orders, List<AlgoOrder> protections) {
        DealTranche tranche = new DealTranche();
        tranche.setId(10L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setOrders(orders);
        tranche.setAlgoOrders(protections);
        return tranche;
    }

    private static Order entryLeg(Order.Status status, String plannedRisk, String plannedSize,
                                  String fill, Long positionId) {
        Order leg = new Order();
        leg.setDealTrancheId(10L);
        leg.setType(Order.Type.ENTRY);
        leg.setStatus(status);
        leg.setPlannedRiskAmount(new BigDecimal(plannedRisk));
        leg.setPlannedSizeContracts(new BigDecimal(plannedSize));
        leg.setAccumulatedFillSize(new BigDecimal(fill));
        leg.setPlannedEntryPrice(ENTRY);
        leg.setPlannedStopPrice(PLANNED_STOP);
        leg.setPlannedContractValue(CONTRACT_VALUE);
        leg.setPositionId(positionId);
        return leg;
    }

    /** Живая отдельная защита с объявленным уровнем — носитель действующего стопа транша. */
    private static AlgoOrder protection(String stopLevel, String size) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(50L);
        algoOrder.setDealTrancheId(10L);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(new BigDecimal(size));
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setValue(new BigDecimal(stopLevel));
        algoOrder.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                new Trigger(stopLoss, null), null));
        return algoOrder;
    }

    private static Position livePosition(Long id, String size) {
        Position position = new Position();
        position.setId(id);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(new BigDecimal(size));
        return position;
    }
}
