package com.example.tradingbot.domain.model.aggregate.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает дискриминатор ветви пары реакций на устаревание данных с его
 * исполнимой спецификацией (docs/spec/market-data-freshness.json, величины
 * {@code protectionBranch} и {@code expiredSettingResolved}; ось популяции
 * объявлена там же, дом осей — docs/rules/market-data-freshness.md
 * §«Оси дискриминатора ветви»).
 *
 * <p>Несущее для этого теста — <b>обе пустоты названы</b>: «защищать нечего»
 * и «покрыто» ведут в одну ветвь, а неразрешимый уровень читается как
 * непокрытый. Умолчание в любую сторону дало бы либо аварийное закрытие
 * того, чего нет, либо молчание над риском без границы.
 */
class MarketDataExpiredBranchTest {

    private final StrategyMarketDataExpiredSetting setting =
            new StrategyMarketDataExpiredSetting(MarketDataExpiredAction.WAIT,
                    MarketDataExpiredAction.GRACEFUL_CLOSE);

    @Test
    @DisplayName("Незащищённой ветвь становится только при живом риске: перечень осей пройден целиком")
    void branchCoversDeclaredPopulation() {
        // Живой риск есть — ветвь решают покрытие и разрешимость уровня.
        assertUnprotected(true, true, true);
        assertProtected(true, true, false);
        assertUnprotected(true, false, true);
        assertUnprotected(true, false, false);
        // Живого риска нет — защищать нечего, обе оставшиеся оси ветви не меняют.
        assertProtected(false, true, true);
        assertProtected(false, true, false);
        // Два члена перечня модель не производит (покрытие считается ОТ риска);
        // формула тотальна и на них, и отвечает той же ветвью.
        assertProtected(false, false, true);
        assertProtected(false, false, false);
    }

    @Test
    @DisplayName("Пустой операнд риска ветвь не переключает: непроставленный признак — не «риск есть»")
    void emptyRiskOperandStaysProtected() {
        assertEquals(MarketDataExpiredAction.WAIT, setting.resolve(null, null, null));
    }

    @Test
    @DisplayName("Агрегатная сторона читает предикаты сделки: покрыт не весь риск — ветвь незащищённой")
    void dealLevelOperandsSelectBranch() {
        Deal covered = deal(tranche(BigDecimal.ONE, protection(BigDecimal.ONE)));
        assertTrue(covered.allTranchesCovered());
        assertFalse(covered.stopUnresolved());
        assertEquals(MarketDataExpiredAction.WAIT,
                setting.resolve(covered.anyTrancheRiskBearing(), covered.allTranchesCovered(),
                        covered.stopUnresolved()));

        Deal partiallyCovered = deal(tranche(BigDecimal.ONE, protection(BigDecimal.ONE)),
                tranche(BigDecimal.ONE));
        assertTrue(partiallyCovered.anyTrancheRiskBearing());
        assertFalse(partiallyCovered.allTranchesCovered());
        assertEquals(MarketDataExpiredAction.GRACEFUL_CLOSE,
                setting.resolve(partiallyCovered.anyTrancheRiskBearing(),
                        partiallyCovered.allTranchesCovered(), partiallyCovered.stopUnresolved()));
    }

    @Test
    @DisplayName("Транш с экспозицией без действующего уровня делает уровень сделки неразрешимым")
    void exposureWithoutStopLevelLeavesStopUnresolved() {
        DealTranche bare = tranche(BigDecimal.ONE);
        assertTrue(bare.isRiskBearing());
        assertFalse(bare.isCovered());
        assertTrue(bare.stopUnresolved());

        // Тейк уровня остановки убытка не несёт: покрытием он не становится.
        DealTranche takeProfitOnly = tranche(BigDecimal.ONE,
                protection(BigDecimal.ONE, AlgoOrder.ConditionType.TAKE_PROFIT));
        assertFalse(takeProfitOnly.isCovered());
        assertTrue(takeProfitOnly.stopUnresolved());

        // Экспозиции нет — покрывать нечего, и уровень не спрашивается.
        DealTranche flat = tranche(BigDecimal.ZERO);
        assertFalse(flat.isRiskBearing());
        assertTrue(flat.isCovered());
        assertFalse(flat.stopUnresolved());
    }

    private void assertUnprotected(Boolean riskBearing, Boolean covered, Boolean stopUnresolved) {
        assertTrue(setting.isUnprotected(riskBearing, covered, stopUnresolved));
        assertEquals(MarketDataExpiredAction.GRACEFUL_CLOSE,
                setting.resolve(riskBearing, covered, stopUnresolved));
    }

    private void assertProtected(Boolean riskBearing, Boolean covered, Boolean stopUnresolved) {
        assertFalse(setting.isUnprotected(riskBearing, covered, stopUnresolved));
        assertEquals(MarketDataExpiredAction.WAIT, setting.resolve(riskBearing, covered, stopUnresolved));
    }

    private Deal deal(DealTranche... tranches) {
        Deal deal = new Deal();
        deal.setTranches(List.of(tranches));
        return deal;
    }

    private DealTranche tranche(BigDecimal entryFilled, AlgoOrder... protections) {
        DealTranche tranche = new DealTranche();
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEpisodeSeq(1);
        tranche.setEntryFilled(entryFilled);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of(protections));
        return tranche;
    }

    private AlgoOrder protection(BigDecimal size) {
        return protection(size, AlgoOrder.ConditionType.STOP_LOSS);
    }

    private AlgoOrder protection(BigDecimal size, AlgoOrder.ConditionType conditionType) {
        AlgoOrder protection = new AlgoOrder();
        protection.setStatus(AlgoOrder.Status.ACTIVE);
        protection.setConditionType(conditionType);
        protection.setSize(size);
        return protection;
    }
}
