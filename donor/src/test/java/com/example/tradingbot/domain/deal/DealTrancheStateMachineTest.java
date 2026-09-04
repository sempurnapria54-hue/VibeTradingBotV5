package com.example.tradingbot.domain.deal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает Java-реализацию контракта переходов транша с его исполнимой
 * спецификацией (docs/spec/deal-tranche-lifecycle.json). Спека
 * прогоняется своим раннером и о Java ничего не знает — без этого теста
 * расхождение кода со спекой не ловится ничем.
 */
class DealTrancheStateMachineTest {

    private final DealTrancheStateMachine stateMachine = new DealTrancheStateMachine(List.of());

    @Test
    @DisplayName("Матрица объявленных рёбер совпадает со спекой: объявленные разрешены, прочие нет")
    void declaredEdgesMatchSpecification() {
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.PRECHECK, DealTranche.Status.ENTRY_SUBMITTED));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.PRECHECK, DealTranche.Status.CLOSED));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.ENTRY_FINALIZED));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.EXIT_PENDING));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.ENTRY_SUBMITTED, DealTranche.Status.CLOSED));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.PROTECTION_SWITCHED));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.MANAGING));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.PROTECTION_SWITCHED, DealTranche.Status.MANAGING));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.MANAGING, DealTranche.Status.ENTRY_SUBMITTED));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.MANAGING, DealTranche.Status.EXIT_PENDING));
        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.EXIT_PENDING, DealTranche.Status.CLOSED));

        // Необъявленные — выборка из тех, что легко написать по ошибке.
        assertFalse(stateMachine.edgeDeclared(DealTranche.Status.PRECHECK, DealTranche.Status.MANAGING));
        assertFalse(stateMachine.edgeDeclared(DealTranche.Status.ENTRY_FINALIZED, DealTranche.Status.CLOSED));
        assertFalse(stateMachine.edgeDeclared(DealTranche.Status.MANAGING, DealTranche.Status.CLOSED));
        assertFalse(stateMachine.edgeDeclared(DealTranche.Status.CLOSED, DealTranche.Status.MANAGING));
        assertFalse(stateMachine.edgeDeclared(null, DealTranche.Status.CLOSED));
    }

    @Test
    @DisplayName("Вход под сворачиванием сделки запрещён даже на объявленном ребре")
    void entryUnderCollapseIsRejected() {
        DealTranche tranche = managingTrancheWithLiveEntry();
        Deal collapsing = deal(Deal.Status.EXIT_PENDING);

        assertTrue(stateMachine.edgeDeclared(DealTranche.Status.MANAGING, DealTranche.Status.ENTRY_SUBMITTED));
        assertFalse(stateMachine.transitionAllowed(tranche, DealTranche.Status.ENTRY_SUBMITTED,
                collapsing, true, true));
    }

    @Test
    @DisplayName("Переоткрытие разрешено только при погашенной экспозиции и живой входной ноге")
    void reopenRequiresFlatExposureAndLiveEntryLeg() {
        Deal active = deal(Deal.Status.ERROR);

        DealTranche flat = managingTrancheWithLiveEntry();
        assertTrue(stateMachine.transitionAllowed(flat, DealTranche.Status.ENTRY_SUBMITTED, active, true, true));

        // Разрешение стратегии снято — переоткрытия нет.
        assertFalse(stateMachine.transitionAllowed(flat, DealTranche.Status.ENTRY_SUBMITTED, active, false, true));

        // Экспозиция не погашена (вошли 8, погасили 5) — переоткрытие
        // удваивало бы риск транша.
        DealTranche exposed = managingTrancheWithLiveEntry();
        exposed.setEntryFilled(BigDecimal.valueOf(8));
        assertFalse(stateMachine.transitionAllowed(exposed, DealTranche.Status.ENTRY_SUBMITTED, active, true, true));

        // Живой входной ноги нет — переоткрывать нечем.
        DealTranche withoutLeg = managingTrancheWithLiveEntry();
        withoutLeg.setOrders(List.of());
        assertFalse(stateMachine.transitionAllowed(withoutLeg, DealTranche.Status.ENTRY_SUBMITTED, active, true, true));
    }

    @Test
    @DisplayName("Терминал транша требует полного графа и отсутствия несомого риска")
    void terminalRequiresCompleteGraphAndNoRisk() {
        Deal active = deal(Deal.Status.ACTIVE);

        DealTranche clean = exitPendingTranche();
        assertTrue(stateMachine.transitionAllowed(clean, DealTranche.Status.CLOSED, active, false, true));

        // Граф неполон — терминал ставить рано.
        assertFalse(stateMachine.transitionAllowed(clean, DealTranche.Status.CLOSED, active, false, false));

        // Транш несёт экспозицию (вошли 8, погасили 5) — терминал признал бы
        // живой риск завершённым.
        DealTranche bearing = exitPendingTranche();
        bearing.setEntryFilled(BigDecimal.valueOf(8));
        assertFalse(stateMachine.transitionAllowed(bearing, DealTranche.Status.CLOSED, active, false, true));

        // Транш держит живую заявку — то же самое.
        DealTranche withLiveOrder = exitPendingTranche();
        withLiveOrder.setOrders(List.of(liveEntryOrder()));
        assertFalse(stateMachine.transitionAllowed(withLiveOrder, DealTranche.Status.CLOSED, active, false, true));
    }

    private DealTranche managingTrancheWithLiveEntry() {
        DealTranche tranche = new DealTranche();
        tranche.setId(1L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(BigDecimal.valueOf(5));
        tranche.setReduceOnlyFilled(BigDecimal.valueOf(5));
        tranche.setOrders(List.of(liveEntryOrder()));
        return tranche;
    }

    private DealTranche exitPendingTranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(2L);
        tranche.setStatus(DealTranche.Status.EXIT_PENDING);
        tranche.setEntryFilled(BigDecimal.valueOf(5));
        tranche.setReduceOnlyFilled(BigDecimal.valueOf(5));
        tranche.setOrders(List.of());
        return tranche;
    }

    private Order liveEntryOrder() {
        Order order = new Order();
        order.setStatus(Order.Status.ACTIVE);
        order.setPositionReducingOnly(false);
        return order;
    }

    private Deal deal(Deal.Status status) {
        Deal deal = new Deal();
        deal.setStatus(status);
        return deal;
    }
}
