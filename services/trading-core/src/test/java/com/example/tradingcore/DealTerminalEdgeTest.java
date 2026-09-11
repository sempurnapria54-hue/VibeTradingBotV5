package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.config.PnlReconciliationProperties;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import com.example.tradingcore.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingcore.domain.command.executor.MarkDealClosedExecutor;
import com.example.tradingcore.domain.command.executor.MarkDealEmergencyClosedExecutor;
import com.example.tradingcore.domain.command.executor.MarkDealErrorExecutor;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.LossStreakCounter;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Терминальные рёбра сделки: что звено ассертит, что пишет само и какие
 * ступени затребует (docs/components/MarkDealClosedExecutor.md,
 * docs/components/MarkDealEmergencyClosedExecutor.md,
 * docs/components/MarkDealErrorExecutor.md).
 *
 * <p><b>Проверяется, чего звено НЕ делает.</b> Три подстановки закрывают
 * сделку молча и на глаз неотличимы от расчёта: ноль вместо непосчитанного
 * числа, «выход по стратегии» вместо причины, посчитанной старшинством, и
 * пересчёт признаков на усечённом графе поверх законных значений. Каждый
 * случай ниже — состояние, на котором наивная реализация закрыла бы сделку
 * успешно.
 *
 * <p>Гейт живого риска берётся НАСТОЯЩИЙ, не подменённый: он и есть
 * инвариант, ради которого ребро гардируется
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class DealTerminalEdgeTest {

    private static final String EXCHANGE = "OKX";
    private static final String SETTLE = "USDT";
    private static final String TENANT = "tn-0001";
    private static final Long ACCOUNT_ID = 4L;
    private static final Long ANCHOR_ID = 12L;

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealActionStateDataService actionStates = mock(DealActionStateDataService.class);
    private final AnomalyReportService reports = mock(AnomalyReportService.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final TenantRiskAppetiteDataService tenants = mock(TenantRiskAppetiteDataService.class);
    private final ExchangeContourProperties contourProperties = new ExchangeContourProperties();
    private final PnlReconciliationProperties toleranceProperties = new PnlReconciliationProperties();

    private ExchangeContourProperties.Contour contour;
    private LossStreakCounter lossStreakCounter;
    private MarkDealClosedExecutor closedExecutor;
    private MarkDealEmergencyClosedExecutor emergencyExecutor;
    private MarkDealErrorExecutor errorExecutor;

    @BeforeEach
    void setUp() {
        contour = new ExchangeContourProperties.Contour();
        contourProperties.setExchanges(new LinkedHashMap<>(Map.of(EXCHANGE, contour)));
        DealReconciliationCalculator reconciliationCalculator =
                new DealReconciliationCalculator(contourProperties, toleranceProperties);
        DealTerminalFeaturesWriter featuresWriter =
                new DealTerminalFeaturesWriter(reconciliationCalculator, contourProperties, reports);
        DealTerminalGate terminalGate = new DealTerminalGate();
        lossStreakCounter = new LossStreakCounter(accounts, tenants);
        closedExecutor = new MarkDealClosedExecutor(dealDataService, actionStates, reconciliationCalculator,
                featuresWriter, terminalGate, lossStreakCounter, reports, outboxWriter);
        emergencyExecutor = new MarkDealEmergencyClosedExecutor(dealDataService, actionStates,
                new DealResultCalculator(contourProperties), reconciliationCalculator, featuresWriter,
                terminalGate, lossStreakCounter, reports, outboxWriter);
        errorExecutor = new MarkDealErrorExecutor(dealDataService, actionStates);
        when(tenants.findByTenantInternalId(anyString())).thenReturn(Optional.empty());
        // Ребро применилось — умолчание МОКА обратное, и это несущее:
        // непрослушанный гард даёт «сделка ушла из-под прохода», то есть
        // тест, забывший о нём, падает, а не проходит молча.
        when(dealDataService.applyTerminalEdge(any(), any())).thenReturn(true);
        when(dealDataService.applyErrorEdge(anyLong())).thenReturn(true);
    }

    // --- штатный терминал ----------------------------------------------

    @Test
    void theCleanTerminalRefusesTheEnteredDealWhoseNumberWasNotComputed() {
        Deal deal = closableDeal(true);
        DealContext dealContext = context(deal, true);

        ServiceCommandExecutionResult result = closedExecutor.execute(command(), anchor(), dealContext);

        assertThat(result.getSuccess()).isFalse();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
    }

    @Test
    void theDealThatNeverEnteredGetsAZeroWithItsCurrencyAndTheNotApplicableBenchmark() {
        Deal deal = closableDeal(false);
        DealContext dealContext = context(deal, true);

        ServiceCommandExecutionResult result = closedExecutor.execute(command(), anchor(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.CLOSED);
        assertThat(deal.getResultProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(deal.getResultProfitCurrency()).isEqualTo(SETTLE);
        assertThat(deal.getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.NOT_APPLICABLE);
        assertThat(deal.getCloseOutcome()).isNull();
    }

    @Test
    void anUnresolvedResultCurrencyIsJournalledAndDoesNotBlockTheTerminal() {
        Deal deal = closableDeal(false);
        DealContext dealContext = context(deal, true, null);

        ServiceCommandExecutionResult result = closedExecutor.execute(command(), anchor(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.CLOSED);
        assertThat(deal.getResultProfitCurrency()).isNull();
        verify(reports).journal(any(), signalWithCode(Constants.Hold.RESULT_CURRENCY_UNRESOLVED));
    }

    @Test
    void theTerminalIsRefusedWhileTheAbsenceOfLiveRiskIsNotProven() {
        Deal deal = closableDeal(false);
        DealContext dealContext = context(deal, false);

        ServiceCommandExecutionResult result = closedExecutor.execute(command(), anchor(), dealContext);

        assertThat(result.getSuccess()).isFalse();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
    }

    @Test
    void theCloseReasonIsTakenBySeniorityOfTrancheReasonsAndNotSubstituted() {
        Deal deal = closableDeal(false);
        deal.setTranches(List.of(terminalTranche(DealTranche.CloseReason.TAKE_PROFIT),
                terminalTranche(DealTranche.CloseReason.RISK_CONTROL)));

        closedExecutor.execute(command(), anchor(), context(deal, true));

        assertThat(deal.getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
    }

    @Test
    void theTerminalClosesLiveSystemExecutionsExceptItsOwnAnchor() {
        Deal deal = closableDeal(false);
        DealActionState anchor = anchor();
        DealContext dealContext = context(deal, true);

        closedExecutor.execute(command(), anchor, dealContext);

        verify(actionStates).skipLiveSystemExecutions(dealContext.getActionStates(), ANCHOR_ID);
    }

    // --- серия убытков --------------------------------------------------

    @Test
    void aPriceLossAdvancesTheStreakAndAPriceProfitResetsIt() {
        Deal losing = finalizedDeal(new BigDecimal("-10"), BigDecimal.ZERO);
        closedExecutor.execute(command(), anchor(), context(losing, true));
        verify(accounts).applyLossStreak(ACCOUNT_ID, true);

        Deal winning = finalizedDeal(new BigDecimal("10"), BigDecimal.ZERO);
        closedExecutor.execute(command(), anchor(), context(winning, true));
        verify(accounts).applyLossStreak(ACCOUNT_ID, false);
    }

    @Test
    void carryIncomeThatCoveredAPriceLossDoesNotResetTheStreak() {
        Deal deal = finalizedDeal(new BigDecimal("5"), new BigDecimal("20"));

        closedExecutor.execute(command(), anchor(), context(deal, true));

        verify(accounts).applyLossStreak(ACCOUNT_ID, false);
    }

    @Test
    void aZeroPriceResultAndAnIncompleteGraphBothLeaveTheStreakAlone() {
        Deal neutral = finalizedDeal(new BigDecimal("-3"), new BigDecimal("3"));
        closedExecutor.execute(command(), anchor(), context(neutral, true));

        // Усечённая загрузка замораживает счётчик, а не читает carry-убыток
        // прибылью; ребро на таком графе не ставится вовсе, поэтому
        // конъюнкт проверяется на самом счётчике.
        Deal onTruncatedGraph = finalizedDeal(new BigDecimal("-10"), BigDecimal.ZERO);
        lossStreakCounter.applyTerminal(context(onTruncatedGraph, false));

        verify(accounts, never()).applyLossStreak(anyLong(), any());
    }

    @Test
    void bothBasesRequestTheirOwnRungAtOnce() {
        contour.setReconciliationExploratory(false);
        Deal deal = finalizedDeal(new BigDecimal("-10"), BigDecimal.ZERO);
        deal.setReconciliationStatus(Deal.ReconciliationStatus.MISMATCHED);
        when(tenants.findByTenantInternalId(TENANT)).thenReturn(Optional.of(tenantWithLimit(1)));

        ServiceCommandExecutionResult result = closedExecutor.execute(command(), anchor(), context(deal, true));

        assertThat(result.getHoldSignals()).extracting(HoldSignal::getCode)
                .containsExactlyInAnyOrder(Constants.Hold.PNL_RECONCILIATION_MISMATCH,
                        Constants.Hold.LOSS_STREAK_LIMIT_REACHED);
    }

    // --- аварийный терминал ---------------------------------------------

    @Test
    void theEmergencyTerminalDoesNotRecomputeANumberThatAlreadyStands() {
        Deal deal = finalizedDeal(new BigDecimal("-10"), BigDecimal.ZERO);
        deal.setStatus(Deal.Status.ERROR);
        deal.setCloseOutcome(Deal.CloseOutcome.NORMAL_EXIT);

        emergencyExecutor.execute(command(), anchor(), context(deal, true, SETTLE, false));

        assertThat(deal.getStatus()).isEqualTo(Deal.Status.EMERGENCY_CLOSED);
        assertThat(deal.getResultProfit()).isEqualByComparingTo(new BigDecimal("-10"));
        assertThat(deal.getCloseOutcome()).isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
    }

    @Test
    void anUnavailableResultLeavesTheNumberEmptyAndIsCounted() {
        Deal deal = closableDeal(true);
        deal.setStatus(Deal.Status.ERROR);
        DealContext dealContext = context(deal, true, SETTLE, false);

        ServiceCommandExecutionResult result = emergencyExecutor.execute(command(), anchor(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.EMERGENCY_CLOSED);
        assertThat(deal.getResultProfit()).isNull();
        verify(reports).journal(any(), signalWithCode(Constants.Hold.RESULT_NOT_COMPUTABLE));
    }

    @Test
    void theEmergencyTerminalIsNotGatedByTrancheTerminality() {
        Deal deal = finalizedDeal(new BigDecimal("-10"), BigDecimal.ZERO);
        deal.setStatus(Deal.Status.ERROR);
        DealTranche live = new DealTranche();
        live.setId(9L);
        live.setStatus(DealTranche.Status.MANAGING);
        deal.setTranches(List.of(live));

        emergencyExecutor.execute(command(), anchor(), context(deal, true));

        assertThat(deal.getStatus()).isEqualTo(Deal.Status.EMERGENCY_CLOSED);
    }

    // --- ребро в ошибочное состояние -------------------------------------

    @Test
    void theErrorEdgeWritesNoCloseReasonAndDoesNotMoveTheStreak() {
        Deal deal = closableDeal(true);
        deal.setStatus(Deal.Status.ACTIVE);

        ServiceCommandExecutionResult result = errorExecutor.execute(command(), anchor(), context(deal, true));

        assertThat(result.getSuccess()).isTrue();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.ERROR);
        assertThat(deal.getCloseReason()).isNull();
        verify(accounts, never()).applyLossStreak(anyLong(), any());
    }

    // --- гард ребра: сделка ушла из-под прохода --------------------------

    /**
     * <b>Каскад жёсткой ступени терминалом не снимается.</b> Проактивная
     * детекция уводит активные сделки счёта в {@code ERROR} своим потоком;
     * гард исходного статуса не пускает терминал, выведенный из снимка
     * начала прохода, и звено не завершается.
     */
    @Test
    void theCleanTerminalStopsWhenTheRowLeftTheActiveStatuses() {
        when(dealDataService.applyTerminalEdge(any(), any())).thenReturn(false);
        Deal deal = closableDeal(false);
        DealContext dealContext = context(deal, true);

        ServiceCommandExecutionResult result = closedExecutor.execute(command(), anchor(), dealContext);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode())
                .as("ошибки нет: звено не завершено, повтор идёт по бюджету строки")
                .isNull();
        assertThat(deal.getStatus())
                .as("модель не объявляет закрытым то, что в базе не закрыто")
                .isEqualTo(Deal.Status.EXIT_PENDING);
        verify(outboxWriter, never()).write(anyString(), any(), any());
    }

    /** Тот же гард у аварийного терминала: он законен ровно из ошибочного. */
    @Test
    void theEmergencyTerminalStopsWhenTheRowIsNoLongerInError() {
        when(dealDataService.applyTerminalEdge(any(), any())).thenReturn(false);
        Deal deal = closableDeal(true);
        deal.setStatus(Deal.Status.ERROR);
        DealContext dealContext = context(deal, true, SETTLE, false);

        ServiceCommandExecutionResult result =
                emergencyExecutor.execute(command(), anchor(), dealContext);

        assertThat(result.getSuccess()).isFalse();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.ERROR);
        verify(outboxWriter, never()).write(anyString(), any(), any());
    }

    /**
     * Ребро в ошибку не применилось — модель за ним не идёт: иначе граф
     * прохода объявил бы ошибочной сделку, которую база уже закрыла.
     */
    @Test
    void theErrorEdgeLeavesTheModelAloneWhenItDidNotApply() {
        when(dealDataService.applyErrorEdge(anyLong())).thenReturn(false);
        Deal deal = closableDeal(false);
        DealActionState anchor = anchor();

        errorExecutor.execute(command(), anchor, context(deal, true));

        assertThat(deal.getStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(anchor.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
    }

    /**
     * <b>Строка целиком не пишется ни одним терминалом.</b> Проба
     * структурная: {@code create} есть единственный писатель строки
     * целиком, и он заводит новую — существующую правят точечные
     * гардированные запросы
     * (docs/models/domain/aggregate/Deal.md §Персистентность).
     */
    @Test
    void noTerminalWritesTheRowWholesale() {
        Deal closable = closableDeal(false);
        closedExecutor.execute(command(), anchor(), context(closable, true));
        emergencyExecutor.execute(command(), anchor(), context(inError(), true, SETTLE, false));
        errorExecutor.execute(command(), anchor(), context(closableDeal(false), true));

        verify(dealDataService, never()).create(any());
    }

    // --- сборка состояния -------------------------------------------------

    private static HoldSignal signalWithCode(String code) {
        return org.mockito.ArgumentMatchers
                .argThat(signal -> java.util.Objects.equals(code, signal.getCode()));
    }

    private static ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND)
                .dealId(1L)
                .dealActionStateId(ANCHOR_ID)
                .build();
    }

    private static DealActionState anchor() {
        DealActionState state = new DealActionState();
        state.setId(ANCHOR_ID);
        state.setDealId(1L);
        state.setSystemActionType(SystemActionType.FINALIZE_DEAL_EXIT_ACTION);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }

    /** Та же сделка, уже уведённая в ошибочное состояние. */
    private static Deal inError() {
        Deal deal = closableDeal(true);
        deal.setStatus(Deal.Status.ERROR);
        return deal;
    }

    /** Сделка, готовая к терминалу: транши терминальны, живого риска нет. */
    private static Deal closableDeal(boolean entered) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(Deal.Status.EXIT_PENDING);
        deal.setTranches(List.of(terminalTranche(DealTranche.CloseReason.STRATEGY_EXIT)));
        deal.setPositions(entered ? List.of(closedEpisode(BigDecimal.ZERO)) : List.of());
        if (entered) {
            deal.setEntryReason(Deal.EntryReason.RECOVERY);
        }
        return deal;
    }

    /** Вошедшая сделка с уже финализированным числом и накопленным финансированием. */
    private static Deal finalizedDeal(BigDecimal resultProfit, BigDecimal fundingCost) {
        Deal deal = closableDeal(true);
        deal.setResultProfit(resultProfit);
        deal.setResultProfitCurrency(SETTLE);
        deal.setPositions(List.of(episodeWithFunding(fundingCost)));
        return deal;
    }

    private static DealTranche terminalTranche(DealTranche.CloseReason reason) {
        DealTranche tranche = new DealTranche();
        tranche.setId(2L);
        tranche.setStatus(DealTranche.Status.CLOSED);
        tranche.setCloseReason(reason);
        return tranche;
    }

    private static Position closedEpisode(BigDecimal net) {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        episode.setExternalSize(BigDecimal.ZERO);
        episode.setExternalRealizedProfit(net);
        episode.setExternalCloseType("1");
        return episode;
    }

    private static Position episodeWithFunding(BigDecimal fundingCost) {
        Position episode = closedEpisode(BigDecimal.ZERO);
        episode.setExternalFundingCost(fundingCost);
        return episode;
    }

    private DealContext context(Deal deal, boolean graphComplete) {
        return context(deal, graphComplete, SETTLE, true);
    }

    private DealContext context(Deal deal, boolean graphComplete, String settleCurrency) {
        return context(deal, graphComplete, settleCurrency, true);
    }

    private DealContext context(Deal deal, boolean graphComplete, String settleCurrency, boolean flowsComplete) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setExchangeCode(EXCHANGE);
        account.setTenantId(TENANT);
        account.setConsecutiveLossCount(0);
        Instrument instrument = new Instrument();
        instrument.setId(5L);
        instrument.setExternalSettlementCurrency(settleCurrency);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .actionStates(new ArrayList<>())
                .cashFlows(new ArrayList<>())
                .graphComplete(graphComplete)
                .flowsComplete(flowsComplete)
                .build();
    }

    private static Tenant tenantWithLimit(Integer limit) {
        Tenant tenant = new Tenant();
        tenant.setInternalId(TENANT);
        tenant.setGlobalConsecutiveLossLimit(limit);
        return tenant;
    }
}
