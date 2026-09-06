package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.config.PnlReconciliationProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealResult;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Итог сделки и исход сверки — исполнимые формы docs/spec/deal-result.json
 * и docs/spec/pnl-reconciliation.json.
 *
 * <p><b>Проверяется НЕДОСТУПНОСТЬ, а не занижение.</b> Каждый случай ниже
 * — состояние, на котором наивная сумма ответила бы числом: пропущенное
 * слагаемое, недогруженная коллекция, строка чужой валюты без курса. Итог
 * там обязан быть пустым, а не меньшим: пропуск слагаемого есть
 * благоприятное умолчание.
 *
 * <p>Состояние собирается настоящими строками — эпизодами с их записями
 * закрытия, ногами с их наливом, строками разбивки с их статусом курса
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class DealResultSpecTest {

    private static final String EXCHANGE = "OKX";
    private static final String SETTLE = "USDT";

    private final ExchangeContourProperties contourProperties = new ExchangeContourProperties();
    private final PnlReconciliationProperties toleranceProperties = new PnlReconciliationProperties();

    private DealResultCalculator resultCalculator;
    private DealReconciliationCalculator reconciliationCalculator;

    @BeforeEach
    void setUp() {
        ExchangeContourProperties.Contour contour = new ExchangeContourProperties.Contour();
        contour.setReconciliationExclusions(List.of("2/407"));
        contourProperties.setExchanges(new java.util.LinkedHashMap<>(java.util.Map.of(EXCHANGE, contour)));
        resultCalculator = new DealResultCalculator(contourProperties);
        reconciliationCalculator = new DealReconciliationCalculator(contourProperties, toleranceProperties);
    }

    // --- итог сделки ---------------------------------------------------

    @Test
    void aDealThatNeverEnteredHasAnAvailableZeroResult() {
        DealContext dealContext = context(dealWithoutEntry(), List.of(), true, true);

        DealResult result = resultCalculator.calculate(dealContext);

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getResultProfitCurrency()).isEqualTo(SETTLE);
    }

    @Test
    void oneEpisodeWithoutACloseRecordMakesTheResultUnavailableRatherThanSmaller() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")), episodeWithoutCloseRecord());

        DealResult result = resultCalculator.calculate(context(deal, List.of(), true, true));

        assertThat(result.getAvailable()).isFalse();
        assertThat(result.getResultProfit()).isNull();
    }

    @Test
    void anIncompleteGraphMakesTheResultUnavailable() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")));

        DealResult result = resultCalculator.calculate(context(deal, List.of(), false, true));

        assertThat(result.getAvailable()).isFalse();
    }

    @Test
    void breakdownThatWasNotFetchedOrWasTruncatedMakesTheResultUnavailable() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")));

        DealResult result = resultCalculator.calculate(context(deal, List.of(), true, false));

        assertThat(result.getAvailable()).isFalse();
    }

    @Test
    void aForeignCurrencyRowAwaitingItsRateBlocksTheResult() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")));
        DealCashFlow pending = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "BTC", new BigDecimal("-1"));
        pending.setRateStatus(DealCashFlow.RateStatus.RATE_UNAVAILABLE);

        DealResult result = resultCalculator.calculate(context(deal, List.of(pending), true, true));

        assertThat(result.getAvailable()).isFalse();
    }

    @Test
    void theUnclassifiedBasketBlocksTheResultWithoutBecomingATerm() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")));
        DealCashFlow unclassified = flow(DealCashFlow.CashFlowCategory.OTHER, "BTC", new BigDecimal("-1"));
        unclassified.setRateStatus(DealCashFlow.RateStatus.APPLIED);
        unclassified.setAppliedRate(new BigDecimal("50000"));

        DealResult result = resultCalculator.calculate(context(deal, List.of(unclassified), true, true));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void aForeignCurrencyTermIsAddedAtTheAppliedRate() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")));
        DealCashFlow converted = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "BTC", new BigDecimal("-0.0001"));
        converted.setRateStatus(DealCashFlow.RateStatus.APPLIED);
        converted.setAppliedRate(new BigDecimal("50000"));

        DealResult result = resultCalculator.calculate(context(deal, List.of(converted), true, true));

        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void theSumRunsOverEveryEpisodeAndNotOnlyTheLastOne() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")), closedEpisode(new BigDecimal("-4")));

        DealResult result = resultCalculator.calculate(context(deal, List.of(), true, true));

        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("6"));
    }

    // --- сверка --------------------------------------------------------

    @Test
    void theDutyThatHasNotArisenGivesNotRunRatherThanMatched() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("10")));
        deal.setCoverageProvenThrough(null);

        assertThat(reconciliationCalculator.reconcile(context(deal, List.of(), true, true)))
                .isEqualTo(Deal.ReconciliationStatus.NOT_RUN);
    }

    @Test
    void combinedAndSeparateFeeGranularityGiveTheSameOutcome() {
        Position episode = reconciledEpisode();
        DealCashFlow combined = flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, SETTLE, new BigDecimal("9"));
        combined.setExternalFee(new BigDecimal("-1"));
        Deal combinedDeal = reconciliationDeal(episode);

        Deal separateDeal = reconciliationDeal(reconciledEpisode());
        DealCashFlow trade = flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, SETTLE, new BigDecimal("10"));
        trade.setExternalFee(new BigDecimal("-1"));
        DealCashFlow fee = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, SETTLE, new BigDecimal("-1"));

        assertThat(reconciliationCalculator.reconcile(context(combinedDeal, List.of(combined), true, true)))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(reconciliationCalculator.reconcile(context(separateDeal, List.of(trade, fee), true, true)))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    void discrepanciesOfOppositeSignsDoNotCancelEachOther() {
        Position episode = reconciledEpisode();
        Deal deal = reconciliationDeal(episode);
        DealCashFlow trade = flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, SETTLE, new BigDecimal("11"));
        DealCashFlow fee = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, SETTLE, new BigDecimal("-2"));

        assertThat(reconciliationCalculator.reconcile(context(deal, List.of(trade, fee), true, true)))
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    void theFundingSignIsTakenBackToTheRawConvention() {
        Position episode = reconciledEpisode();
        episode.setExternalFundingCost(new BigDecimal("3"));
        Deal deal = reconciliationDeal(episode);
        DealCashFlow trade = flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, SETTLE, new BigDecimal("10"));
        DealCashFlow fee = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, SETTLE, new BigDecimal("-1"));
        DealCashFlow funding = flow(DealCashFlow.CashFlowCategory.FUNDING, SETTLE, new BigDecimal("-3"));

        assertThat(reconciliationCalculator.reconcile(context(deal, List.of(trade, fee, funding), true, true)))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    void aDealWithoutEntryLegsFallsBackToTheFloorAtAnyTurnover() {
        toleranceProperties.setRelativeShare(new BigDecimal("0.5"));
        toleranceProperties.setOmissionMultiplier(new BigDecimal("2"));
        toleranceProperties.setFloor(new BigDecimal("0.5"));
        Deal deal = reconciliationDeal(reconciledEpisode());
        deal.setTranches(List.of(new DealTranche()));
        DealCashFlow trade = flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, SETTLE, new BigDecimal("11"));
        DealCashFlow fee = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, SETTLE, new BigDecimal("-1"));

        assertThat(reconciliationCalculator.reconcile(context(deal, List.of(trade, fee), true, true)))
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    void anExploratoryToleranceDoesNotRequestARungWhileALiveOneDoes() {
        DealContext dealContext = context(reconciliationDeal(reconciledEpisode()), List.of(), true, true);

        assertThat(reconciliationCalculator.rungRequested(dealContext,
                Deal.ReconciliationStatus.MISMATCHED)).isFalse();

        contourProperties.forExchange(EXCHANGE);
        contourProperties.getExchanges().get(EXCHANGE).setReconciliationExploratory(false);

        assertThat(reconciliationCalculator.rungRequested(dealContext,
                Deal.ReconciliationStatus.MISMATCHED)).isTrue();
        assertThat(reconciliationCalculator.rungRequested(dealContext,
                Deal.ReconciliationStatus.MATCHED)).isFalse();
    }

    // --- сборка состояния ----------------------------------------------

    private static DealCashFlow flow(DealCashFlow.CashFlowCategory category, String ccy, BigDecimal amount) {
        DealCashFlow cashFlow = new DealCashFlow();
        cashFlow.setCategory(category);
        cashFlow.setCcy(ccy);
        cashFlow.setAmount(amount);
        cashFlow.setExternalType("2");
        return cashFlow;
    }

    private static Position closedEpisode(BigDecimal net) {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        episode.setExternalRealizedProfit(net);
        episode.setExternalCloseType("1");
        return episode;
    }

    private static Position episodeWithoutCloseRecord() {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        return episode;
    }

    /** Эпизод, чьи четыре числа сверки заданы: net 10, комиссия −1. */
    private static Position reconciledEpisode() {
        Position episode = closedEpisode(new BigDecimal("9"));
        episode.setExternalRealizedProfitGross(new BigDecimal("10"));
        episode.setExternalFee(new BigDecimal("-1"));
        episode.setExternalFundingCost(BigDecimal.ZERO);
        episode.setExternalLiquidationPenalty(BigDecimal.ZERO);
        return episode;
    }

    private static Deal enteredDeal(Position... episodes) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        deal.setPositions(List.of(episodes));
        deal.setTranches(List.of(new DealTranche()));
        deal.setCoverageProvenThrough(OffsetDateTime.parse("2026-09-06T10:00:00Z"));
        deal.setBillsFetchedThrough(OffsetDateTime.parse("2026-09-06T11:00:00Z"));
        return deal;
    }

    private static Deal reconciliationDeal(Position episode) {
        Deal deal = enteredDeal(episode);
        DealTranche tranche = new DealTranche();
        tranche.setId(2L);
        tranche.setOrders(List.of(entryLeg()));
        deal.setTranches(List.of(tranche));
        return deal;
    }

    /** Нога входа с наливом: множество омиссионного члена — предикат взятого риска. */
    private static Order entryLeg() {
        Order leg = new Order();
        leg.setId(3L);
        leg.setType(Order.Type.ENTRY);
        leg.setStatus(Order.Status.COMPLETED);
        leg.setPlannedRiskAmount(new BigDecimal("10"));
        leg.setPlannedEntryPrice(new BigDecimal("3000"));
        leg.setPlannedStopPrice(new BigDecimal("2900"));
        leg.setPlannedSizeContracts(new BigDecimal("1"));
        leg.setPlannedContractValue(new BigDecimal("0.1"));
        leg.setAccumulatedFillSize(new BigDecimal("1"));
        return leg;
    }

    private static Deal dealWithoutEntry() {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setTranches(List.of(new DealTranche()));
        return deal;
    }

    private static DealContext context(Deal deal, List<DealCashFlow> cashFlows, boolean graphComplete,
                                       boolean flowsComplete) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(4L);
        account.setExchangeCode(EXCHANGE);
        account.setTenantId("tn-0001");
        Instrument instrument = new Instrument();
        instrument.setId(5L);
        instrument.setExternalSettlementCurrency(SETTLE);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .cashFlows(new ArrayList<>(cashFlows))
                .graphComplete(graphComplete)
                .flowsComplete(flowsComplete)
                .build();
    }
}
