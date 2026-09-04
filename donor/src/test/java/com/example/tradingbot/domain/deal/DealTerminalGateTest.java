package com.example.tradingbot.domain.deal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает Java-реализацию гейта терминала с его исполнимой
 * спецификацией (docs/spec/deal-lifecycle.json §riskProvenAbsent,
 * docs/spec/protection-coverage.json §exposureReconciled).
 *
 * <p>Несущее для этого теста — ТОТАЛЬНОСТЬ правой стороны сверки: гейт
 * вычисляется всегда там, где живого эпизода нет, и обе штатные формы
 * его отсутствия (строки позиции нет вовсе; строка закрыта с
 * необнулённым размером) обязаны давать сходящуюся сверку. Прежняя
 * строгая форма на первой отказывала вычислением, на второй была вечно
 * ложной — сделка не закрывалась ни на одной.
 */
class DealTerminalGateTest {

    private final DealTerminalGate gate = new DealTerminalGate();

    @Test
    @DisplayName("Эпизода не было вовсе: правого операнда нет, сверка сходится нулём")
    void reconcilesWhenNoPositionRowExists() {
        Deal deal = deal(null);

        assertFalse(gate.hasLiveEpisode(null));
        assertTrue(gate.exposureReconciled(null, List.of()));
        assertTrue(gate.riskProvenAbsent(deal, List.of(), true));
    }

    @Test
    @DisplayName("Эпизод закрыт, размер строки не обнулён: сверка сходится нулём, терминал открыт")
    void reconcilesWhenClosedRowKeepsStaleSize() {
        Position stale = position(Position.Status.CLOSED, BigDecimal.valueOf(40));
        Deal deal = deal(stale);
        List<DealTranche> tranches = List.of(closedTranche(BigDecimal.valueOf(40), BigDecimal.valueOf(40)));

        assertFalse(gate.hasLiveEpisode(stale));
        assertTrue(gate.exposureReconciled(stale, tranches));
        assertTrue(gate.riskProvenAbsent(deal, tranches, true));
    }

    @Test
    @DisplayName("Живой эпизод: сверка идёт против нетто-размера, а не против нуля")
    void reconcilesAgainstNetSizeWhenEpisodeIsLive() {
        Position live = position(Position.Status.ACTIVE, BigDecimal.valueOf(3));
        List<DealTranche> matching = List.of(openTranche(BigDecimal.valueOf(3)));
        List<DealTranche> mismatching = List.of(openTranche(BigDecimal.valueOf(2)));

        assertTrue(gate.hasLiveEpisode(live));
        assertTrue(gate.exposureReconciled(live, matching));
        assertFalse(gate.exposureReconciled(live, mismatching));
    }

    @Test
    @DisplayName("Экспозиция траншей при отсутствии живого эпизода — расхождение, терминал закрыт")
    void blocksTerminalWhenTranchesStillCarryExposure() {
        Position stale = position(Position.Status.CLOSED, BigDecimal.ZERO);
        Deal deal = deal(stale);
        // Вошли 5, погасили 2: остаток 3 закрыт кем-то извне и траншам не приписан.
        List<DealTranche> tranches = List.of(closedTranche(BigDecimal.valueOf(5), BigDecimal.valueOf(2)));

        assertFalse(gate.exposureReconciled(stale, tranches));
        assertFalse(gate.riskProvenAbsent(deal, tranches, true));
    }

    @Test
    @DisplayName("Неполный граф, живой эпизод и живая заявка — каждый сам по себе закрывает терминал")
    void eachConjunctBlocksTerminalOnItsOwn() {
        Deal cleanDeal = deal(null);
        assertFalse(gate.riskProvenAbsent(cleanDeal, List.of(), false));

        Position live = position(Position.Status.ACTIVE, BigDecimal.valueOf(3));
        assertFalse(gate.riskProvenAbsent(deal(live), List.of(openTranche(BigDecimal.valueOf(3))), true));

        DealTranche withLiveOrder = closedTranche(BigDecimal.valueOf(5), BigDecimal.valueOf(5));
        withLiveOrder.setOrders(List.of(liveOrder()));
        assertFalse(gate.riskProvenAbsent(deal(null), List.of(withLiveOrder), true));
    }

    private Deal deal(Position position) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setPositions(java.util.Objects.isNull(position) ? java.util.List.of() : java.util.List.of(position));
        return deal;
    }

    private Position position(Position.Status status, BigDecimal externalSize) {
        Position position = new Position();
        position.setStatus(status);
        position.setExternalSize(externalSize);
        return position;
    }

    private DealTranche closedTranche(BigDecimal entryFilled, BigDecimal reduceOnlyFilled) {
        DealTranche tranche = new DealTranche();
        tranche.setStatus(DealTranche.Status.CLOSED);
        tranche.setEntryFilled(entryFilled);
        tranche.setReduceOnlyFilled(reduceOnlyFilled);
        tranche.setOrders(List.of());
        return tranche;
    }

    private DealTranche openTranche(BigDecimal exposure) {
        DealTranche tranche = new DealTranche();
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(exposure);
        tranche.setOrders(List.of());
        return tranche;
    }

    private Order liveOrder() {
        Order order = new Order();
        order.setStatus(Order.Status.ACTIVE);
        order.setPositionReducingOnly(false);
        return order;
    }
}
