package com.example.tradingbot.domain.deal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает матрицу рёбер и гейт терминала сделки с их исполнимой
 * спецификацией (docs/spec/deal-lifecycle.json §edgeDeclared,
 * §riskProvenAbsent, §transitionAllowed).
 *
 * <p>Несущее: статусы сделки — АГРЕГАТНЫЕ. Стадии входа и сопровождения
 * принадлежат траншу, и их в матрице сделки нет вовсе.
 */
class DealStateMachineTest {

    private final DealStateMachine stateMachine =
            new DealStateMachine(List.of(), new DealTerminalGate());

    @Test
    @DisplayName("Матрица рёбер сделки совпадает со спекой")
    void declaredEdgesMatchSpecification() {
        assertTrue(stateMachine.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.EXIT_PENDING));
        assertTrue(stateMachine.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.CLOSED));
        assertTrue(stateMachine.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.ERROR));
        assertTrue(stateMachine.edgeDeclared(Deal.Status.EXIT_PENDING, Deal.Status.CLOSED));
        assertTrue(stateMachine.edgeDeclared(Deal.Status.EXIT_PENDING, Deal.Status.ERROR));
        assertTrue(stateMachine.edgeDeclared(Deal.Status.ERROR, Deal.Status.EMERGENCY_CLOSED));

        // Аварийный терминал достижим ТОЛЬКО через ошибочную тропу.
        assertFalse(stateMachine.edgeDeclared(Deal.Status.ACTIVE, Deal.Status.EMERGENCY_CLOSED));
        assertFalse(stateMachine.edgeDeclared(Deal.Status.EXIT_PENDING, Deal.Status.EMERGENCY_CLOSED));
        // Терминал терминален: обратных рёбер нет.
        assertFalse(stateMachine.edgeDeclared(Deal.Status.CLOSED, Deal.Status.ACTIVE));
        assertFalse(stateMachine.edgeDeclared(Deal.Status.ERROR, Deal.Status.CLOSED));
        assertFalse(stateMachine.edgeDeclared(null, Deal.Status.CLOSED));
    }

    @Test
    @DisplayName("Сделка без входа доходит до терминала: эпизода нет, транши пусты")
    void dealWithoutEntryReachesTerminal() {
        DealContext context = context(deal(Deal.Status.ACTIVE, null, List.of()));

        assertTrue(stateMachine.transitionAllowed(context, Deal.Status.CLOSED, true));
    }

    @Test
    @DisplayName("Закрытая строка эпизода с необнулённым размером терминал не держит")
    void staleClosedPositionDoesNotBlockTerminal() {
        Deal deal = deal(Deal.Status.EXIT_PENDING,
                position(Position.Status.CLOSED, BigDecimal.valueOf(40)),
                List.of(closedTranche(BigDecimal.valueOf(40), BigDecimal.valueOf(40))));

        assertTrue(stateMachine.transitionAllowed(context(deal), Deal.Status.CLOSED, true));
    }

    @Test
    @DisplayName("Живой транш и живой эпизод терминал не пропускают")
    void liveRiskBlocksTerminal() {
        Deal withLiveTranche = deal(Deal.Status.EXIT_PENDING, null,
                List.of(openTranche(BigDecimal.valueOf(3))));
        assertFalse(stateMachine.transitionAllowed(context(withLiveTranche), Deal.Status.CLOSED, true));

        Deal withLiveEpisode = deal(Deal.Status.EXIT_PENDING,
                position(Position.Status.ACTIVE, BigDecimal.valueOf(3)),
                List.of(closedTranche(BigDecimal.valueOf(3), BigDecimal.valueOf(3))));
        assertFalse(stateMachine.transitionAllowed(context(withLiveEpisode), Deal.Status.CLOSED, true));
    }

    @Test
    @DisplayName("Аварийный терминал тоже под гейтом живого риска")
    void emergencyTerminalIsGatedToo() {
        Deal clean = deal(Deal.Status.ERROR, null, List.of());
        assertTrue(stateMachine.transitionAllowed(context(clean), Deal.Status.EMERGENCY_CLOSED, true));

        Deal bearing = deal(Deal.Status.ERROR, null, List.of(openTranche(BigDecimal.valueOf(2))));
        assertFalse(stateMachine.transitionAllowed(context(bearing), Deal.Status.EMERGENCY_CLOSED, true));
    }

    @Test
    @DisplayName("Нетерминальное ребро гейтом живого риска не проверяется")
    void nonTerminalEdgeIsNotGatedByRisk() {
        Deal bearing = deal(Deal.Status.ACTIVE, position(Position.Status.ACTIVE, BigDecimal.valueOf(5)),
                List.of(openTranche(BigDecimal.valueOf(5))));

        assertTrue(stateMachine.transitionAllowed(context(bearing), Deal.Status.EXIT_PENDING, true));
    }

    private DealContext context(Deal deal) {
        return DealContext.builder().deal(deal).build();
    }

    private Deal deal(Deal.Status status, Position position, List<DealTranche> tranches) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(status);
        deal.setPosition(position);
        deal.setTranches(tranches);
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
}
