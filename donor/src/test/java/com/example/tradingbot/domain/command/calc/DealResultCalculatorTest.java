package com.example.tradingbot.domain.command.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.config.ExchangeContourProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает расчёт итогового результата сделки с его исполнимой формой
 * (docs/spec/deal-result.json).
 *
 * <p>Несущее — <b>недоступность вместо занижения</b>: недобытая запись
 * закрытия, неполная разбивка и строка чужой валюты без курса оставляют
 * число ПУСТЫМ, а не подставляют ноль или сумму без слагаемого. И
 * <b>асимметрия областей</b>: блокировка шире слагаемого — принимающая
 * корзина в слагаемое не входит, а итог блокирует.
 */
class DealResultCalculatorTest {

    private static final String SETTLE = "USDT";

    private final DealResultCalculator calculator = new DealResultCalculator(contourProperties());

    @Test
    @DisplayName("Итог — СУММА net по эпизодам, а не число последнего")
    void resultIsSumOverEpisodes() {
        DealResult result = calculator.calculate(context(deal(episode(12.5), episode(-4)), List.of(), true, true));

        assertTrue(result.getAvailable());
        assertEquals(0, result.getResultProfit().compareTo(BigDecimal.valueOf(8.5)));
        assertEquals(SETTLE, result.getResultProfitCurrency());
    }

    @Test
    @DisplayName("Недобытая запись закрытия делает итог НЕДОСТУПНЫМ, а не заниженным")
    void missingCloseRecordMakesResultUnavailable() {
        DealResult result = calculator.calculate(
                context(deal(episode(12.5), episode(null)), List.of(), true, true));

        assertFalse(result.getAvailable());
        assertNull(result.getResultProfit());
    }

    @Test
    @DisplayName("Неполный граф итог не занижает, а отказывает: агрегат по подмножеству истинен молча")
    void incompleteGraphMakesResultUnavailable() {
        DealResult result = calculator.calculate(context(deal(episode(12.5)), List.of(), false, true));

        assertFalse(result.getAvailable());
        assertNull(result.getResultProfit());
    }

    @Test
    @DisplayName("Недобытая разбивка итог не публикует: слагаемое чужой валюты вышло бы молча нулевым")
    void incompleteFlowsMakeResultUnavailable() {
        DealResult result = calculator.calculate(context(deal(episode(100)), List.of(), true, false));

        assertFalse(result.getAvailable());
        assertNull(result.getResultProfit());
    }

    @Test
    @DisplayName("Движение чужой валюты с применённым курсом входит слагаемым")
    void appliedRateFlowEntersTerm() {
        DealCashFlow flow = flow("BTC", -0.002, DealCashFlow.CashFlowCategory.TRADE_FEE,
                DealCashFlow.RateStatus.APPLIED, 30000);

        DealResult result = calculator.calculate(context(deal(episode(100)), List.of(flow), true, true));

        assertTrue(result.getAvailable());
        assertEquals(0, result.getResultProfit().compareTo(BigDecimal.valueOf(40)));
    }

    @Test
    @DisplayName("Строка чужой валюты без курса блокирует итог")
    void pendingRateBlocksResult() {
        DealCashFlow flow = flow("BTC", -0.002, DealCashFlow.CashFlowCategory.TRADE_FEE,
                DealCashFlow.RateStatus.RATE_UNAVAILABLE, null);

        DealResult result = calculator.calculate(context(deal(episode(100)), List.of(flow), true, true));

        assertFalse(result.getAvailable());
        assertNull(result.getResultProfit());
    }

    @Test
    @DisplayName("Нераспознанная категория без курса БЛОКИРУЕТ, хотя в слагаемое не входит")
    void unclassifiedFlowBlocksButDoesNotContribute() {
        DealCashFlow blocking = flow("BTC", -1, DealCashFlow.CashFlowCategory.OTHER,
                DealCashFlow.RateStatus.RATE_UNAVAILABLE, null);
        DealCashFlow applied = flow("BTC", -1, DealCashFlow.CashFlowCategory.OTHER,
                DealCashFlow.RateStatus.APPLIED, 30000);

        assertFalse(calculator.calculate(context(deal(episode(100)), List.of(blocking), true, true))
                .getAvailable());
        DealResult withApplied = calculator.calculate(
                context(deal(episode(100)), List.of(applied), true, true));
        assertTrue(withApplied.getAvailable());
        // Область слагаемого корзину не включает: 100, а не 100 − 30000.
        assertEquals(0, withApplied.getResultProfit().compareTo(BigDecimal.valueOf(100)));
    }

    @Test
    @DisplayName("Тропа закрытия без входа: ноль как РЕЗУЛЬТАТ расчёта, и у него есть валюта")
    void noEntryPathYieldsComputedZero() {
        Deal deal = new Deal();
        deal.setEntryReason(Deal.EntryReason.STRATEGY);
        deal.setTranches(List.of());
        deal.setPositions(List.of());

        DealResult result = calculator.calculate(context(deal, List.of(), true, false));

        assertTrue(result.getAvailable());
        assertEquals(0, result.getResultProfit().compareTo(BigDecimal.ZERO));
        assertEquals(SETTLE, result.getResultProfitCurrency());
    }

    // ------------------------------------------------------------------

    private ExchangeContourProperties contourProperties() {
        return new ExchangeContourProperties();
    }

    private DealContext context(Deal deal, List<DealCashFlow> cashFlows, boolean graphComplete,
                                boolean flowsComplete) {
        Instrument instrument = new Instrument();
        instrument.setExternalSettlementCurrency(SETTLE);
        Exchange exchange = new Exchange();
        exchange.setName("OKX");
        return DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .exchange(exchange)
                .cashFlows(cashFlows)
                .graphComplete(graphComplete)
                .flowsComplete(flowsComplete)
                .build();
    }

    /** Сделка с наблюдённой позицией: признак входа резолвится RECOVERY-тропой. */
    private Deal deal(Position... episodes) {
        Deal deal = new Deal();
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        deal.setTranches(List.of());
        deal.setPositions(List.of(episodes));
        return deal;
    }

    private Position episode(Number realizedProfit) {
        Position position = new Position();
        if (realizedProfit != null) {
            position.setExternalRealizedProfit(BigDecimal.valueOf(realizedProfit.doubleValue()));
        }
        return position;
    }

    private DealCashFlow flow(String ccy, Number amount, DealCashFlow.CashFlowCategory category,
                              DealCashFlow.RateStatus rateStatus, Number appliedRate) {
        DealCashFlow flow = new DealCashFlow();
        flow.setCcy(ccy);
        flow.setAmount(BigDecimal.valueOf(amount.doubleValue()));
        flow.setCategory(category);
        flow.setRateStatus(rateStatus);
        if (appliedRate != null) {
            flow.setAppliedRate(BigDecimal.valueOf(appliedRate.doubleValue()));
        }
        return flow;
    }
}
