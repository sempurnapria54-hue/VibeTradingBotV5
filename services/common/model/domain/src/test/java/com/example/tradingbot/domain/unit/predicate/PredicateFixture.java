package com.example.tradingbot.domain.unit.predicate;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Сборка состояния для кейсов предмета {@code domain-model-predicates}
 * (.claude/tests/cases/domain-model-predicates.md).
 *
 * <p><b>Ни один предикат здесь не подменяется.</b> Сборка ставит
 * НАСТОЯЩИЕ поля моделей — налив, статус, размер, состав коллекций, — и
 * предикат считается по ним. Тест, подменивший предикат доменной модели,
 * проверял бы исполнителя против модели, которой в проде не существует
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 *
 * <p>Субстрата у предмета нет вовсе: ни контейнеров, ни контекста Spring,
 * ни стабов — модель конструируется {@code new} и наполняется сеттерами.
 */
final class PredicateFixture {

    private PredicateFixture() {
    }

    /** Десятичное из строки; {@code null} остаётся пустотой. */
    static BigDecimal dec(String raw) {
        return raw == null ? null : new BigDecimal(raw);
    }

    /** Транш без заявок: только четыре слагаемых экспозиции. */
    static DealTranche tranche(String entryFilled, String reduceOnlyFilled,
                               String protectionClosed, String closeAttributed) {
        DealTranche tranche = new DealTranche();
        tranche.setEntryFilled(dec(entryFilled));
        tranche.setReduceOnlyFilled(dec(reduceOnlyFilled));
        tranche.setProtectionClosed(dec(protectionClosed));
        tranche.setCloseAttributed(dec(closeAttributed));
        return tranche;
    }

    /** Транш с экспозицией из одного налива входа. */
    static DealTranche trancheWithExposure(String entryFilled) {
        return tranche(entryFilled, null, null, null);
    }

    /** Обычная заявка: идентичность строки, статус, налив, признак уменьшения. */
    static Order order(Long id, Order.Status status, String accumulatedFillSize, Boolean reducingOnly) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setAccumulatedFillSize(dec(accumulatedFillSize));
        order.setPositionReducingOnly(reducingOnly);
        return order;
    }

    /** Встроенная защита: статус, объявленный размер, уровень остановки убытка. */
    static AttachedAlgoOrder attached(AttachedAlgoOrder.Status status, String size, String stopLevel) {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setStatus(status);
        protection.setSize(dec(size));
        protection.setStopLossTriggerPrice(dec(stopLevel));
        return protection;
    }

    /** Заявка со встроенными защитами. */
    static Order orderWith(Order order, AttachedAlgoOrder... protections) {
        order.setAttachedAlgoOrders(new ArrayList<>(Arrays.asList(protections)));
        return order;
    }

    /** Отдельная условная заявка с триггерной ценой остановки убытка. */
    static AlgoOrder standaloneStop(Long id, AlgoOrder.Status status, String size, String stopLevel) {
        AlgoOrder algo = new AlgoOrder();
        algo.setId(id);
        algo.setStatus(status);
        algo.setSize(dec(size));
        algo.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algo.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                new Trigger(new TriggerPrice(null, dec(stopLevel), null, null), null), null));
        return algo;
    }

    /** Отдельная условная заявка типа тейк-профита: действующего уровня не несёт. */
    static AlgoOrder standaloneTakeProfit(Long id, AlgoOrder.Status status, String size) {
        AlgoOrder algo = new AlgoOrder();
        algo.setId(id);
        algo.setStatus(status);
        algo.setSize(dec(size));
        algo.setConditionType(AlgoOrder.ConditionType.TAKE_PROFIT);
        algo.setCondition(new Condition(AlgoOrder.ConditionType.TAKE_PROFIT, null, null));
        return algo;
    }

    /** Трейлинг: уровень несёт только наблюдённая биржей цена. */
    static AlgoOrder standaloneTrailing(Long id, AlgoOrder.Status status, String size, String observedPrice) {
        AlgoOrder algo = new AlgoOrder();
        algo.setId(id);
        algo.setStatus(status);
        algo.setSize(dec(size));
        algo.setConditionType(AlgoOrder.ConditionType.TRAILING_PERCENTS);
        Trailing trailing = new Trailing(dec("1"), null,
                new TriggerPrice(null, dec("500"), null, null), dec(observedPrice));
        algo.setCondition(new Condition(AlgoOrder.ConditionType.TRAILING_PERCENTS, null, trailing));
        return algo;
    }

    /** Транш с предъявленными коллекциями. */
    static DealTranche trancheOf(DealTranche tranche, List<Order> orders, List<AlgoOrder> algoOrders) {
        tranche.setOrders(orders);
        tranche.setAlgoOrders(algoOrders);
        return tranche;
    }

    /** Эпизод позиции: статус, нетто-размер, готовый нетто-результат. */
    static Position episode(Position.Status status, String externalSize, String realizedProfit) {
        Position episode = new Position();
        episode.setStatus(status);
        episode.setExternalSize(dec(externalSize));
        episode.setExternalRealizedProfit(dec(realizedProfit));
        return episode;
    }

    /** Сделка со статусом, направлением и коллекцией траншей. */
    static Deal deal(Deal.Status status, DealTranche... tranches) {
        Deal deal = new Deal();
        deal.setStatus(status);
        deal.setTranches(new ArrayList<>(Arrays.asList(tranches)));
        return deal;
    }

    /** Момент с фиксированным смещением: часов ни один предикат не читает. */
    static OffsetDateTime at(int minute) {
        return OffsetDateTime.parse("2026-09-19T10:00:00Z").plusMinutes(minute);
    }

    /**
     * Покрытие транша, прочитанное публичной поверхностью: исключается
     * идентичность, которой у защит транша нет, поэтому сумма полная.
     */
    static BigDecimal coverageOf(DealTranche tranche) {
        return tranche.coverageWithoutAlgoOrder(Long.MIN_VALUE);
    }
}
