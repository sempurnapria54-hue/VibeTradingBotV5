package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.config.PnlReconciliationProperties;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import com.example.tradingcore.domain.command.calc.DealTerminalFeaturesWriter;
import com.example.tradingcore.domain.command.executor.FinalizeDealEntryExecutor;
import com.example.tradingcore.domain.command.executor.FinalizeDealExitExecutor;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Звенья финализации: чем они завершаются и чем не завершаются
 * (docs/components/FinalizeDealEntryExecutor.md,
 * docs/components/FinalizeDealExitExecutor.md).
 *
 * <p><b>Проверяется, что звено НЕ завершается на недоступном итоге.</b>
 * Завершённое звено уводит действие к терминальному ребру, а то требует
 * числа: подстановка на этом шаге закрыла бы сделку числом, которого никто
 * не считал. Второе — статусное ребро транша: оно едет транзакцией
 * завершения, и разрыв между ними даёт вторую консолидацию на рестарте.
 */
class FinalizationLinkTest {

    private static final String EXCHANGE = "OKX";
    private static final String SETTLE = "USDT";
    private static final Long TRANCHE_ID = 2L;

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealActionStateDataService actionStates = mock(DealActionStateDataService.class);
    private final DealTrancheDataService tranches = mock(DealTrancheDataService.class);
    private final AnomalyReportService reports = mock(AnomalyReportService.class);
    private final ExchangeContourProperties contourProperties = new ExchangeContourProperties();
    private final PnlReconciliationProperties toleranceProperties = new PnlReconciliationProperties();

    private FinalizeDealEntryExecutor entryExecutor;
    private FinalizeDealExitExecutor exitExecutor;

    @BeforeEach
    void setUp() {
        contourProperties.setExchanges(new LinkedHashMap<>(
                Map.of(EXCHANGE, new ExchangeContourProperties.Contour())));
        DealReconciliationCalculator reconciliationCalculator =
                new DealReconciliationCalculator(contourProperties, toleranceProperties);
        entryExecutor = new FinalizeDealEntryExecutor(actionStates, tranches);
        exitExecutor = new FinalizeDealExitExecutor(dealDataService, actionStates,
                new DealResultCalculator(contourProperties),
                new DealTerminalFeaturesWriter(reconciliationCalculator, contourProperties, reports));
    }

    // --- консолидация входа -----------------------------------------------

    @Test
    void theEntryConsolidationRefusesAnAnchorThatNamesNoTranche() {
        DealActionState anchor = anchor(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, null);

        ServiceCommandExecutionResult result = entryExecutor.execute(
                command(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND), anchor,
                context(dealWith(tranche(DealTranche.Status.ENTRY_SUBMITTED))));

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.INTERNAL_ERROR);
    }

    @Test
    void theEntryConsolidationRefusesATrancheTheGraphDoesNotCarry() {
        DealActionState anchor = anchor(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, 404L);

        ServiceCommandExecutionResult result = entryExecutor.execute(
                command(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND), anchor,
                context(dealWith(tranche(DealTranche.Status.ENTRY_SUBMITTED))));

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.INTERNAL_ERROR);
    }

    @Test
    void theTrancheEdgeRidesTheSameTransactionAsTheCompletionOfTheExecution() {
        DealTranche tranche = tranche(DealTranche.Status.ENTRY_SUBMITTED);
        DealActionState anchor = anchor(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, TRANCHE_ID);

        ServiceCommandExecutionResult result = entryExecutor.execute(
                command(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND), anchor, context(dealWith(tranche)));

        assertThat(result.getSuccess()).isTrue();
        assertThat(tranche.getStatus()).isEqualTo(DealTranche.Status.ENTRY_FINALIZED);
        assertThat(anchor.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
        verify(tranches).save(tranche);
    }

    @Test
    void aRepeatOnAnAlreadyConfirmedEntryDoesNotDragTheTrancheBack() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        DealActionState anchor = anchor(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, TRANCHE_ID);

        entryExecutor.execute(command(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND), anchor,
                context(dealWith(tranche)));

        assertThat(tranche.getStatus()).isEqualTo(DealTranche.Status.MANAGING);
        verify(tranches, never()).save(any());
    }

    // --- финализация выхода ------------------------------------------------

    @Test
    void anUnavailableResultDoesNotCompleteTheLinkAndIsClassifiedRetryable() {
        Deal deal = enteredDeal(episodeWithoutCloseRecord());
        DealActionState anchor = anchor(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, null);

        ServiceCommandExecutionResult result = exitExecutor.execute(
                command(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND), anchor, context(deal));

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
        assertThat(deal.getResultProfit()).isNull();
        assertThat(anchor.getStatus()).isEqualTo(DealActionStateStatus.PLANNED);
    }

    @Test
    void anAvailableResultWritesTheNumberItsCurrencyAndTheFeaturesAtOnce() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("12")));
        DealActionState anchor = anchor(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, null);

        ServiceCommandExecutionResult result = exitExecutor.execute(
                command(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND), anchor, context(deal));

        assertThat(result.getSuccess()).isTrue();
        assertThat(deal.getResultProfit()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(deal.getResultProfitCurrency()).isEqualTo(SETTLE);
        assertThat(deal.getCloseOutcome()).isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
        assertThat(deal.getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.MISSING);
        assertThat(anchor.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
    }

    @Test
    void aRepeatOnAnAlreadyComputedNumberIsANoOp() {
        Deal deal = enteredDeal(closedEpisode(new BigDecimal("12")));
        deal.setResultProfit(new BigDecimal("7"));
        DealActionState anchor = anchor(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, null);

        exitExecutor.execute(command(ServiceCommandType.FINALIZE_DEAL_EXIT_COMMAND), anchor, context(deal));

        assertThat(deal.getResultProfit()).isEqualByComparingTo(new BigDecimal("7"));
        assertThat(anchor.getStatus()).isEqualTo(DealActionStateStatus.COMPLETED);
        verify(dealDataService, never()).applyResultAndFeatures(any());
    }

    // --- сборка состояния ---------------------------------------------------

    private static ServiceCommand command(ServiceCommandType type) {
        return ServiceCommand.builder().type(type).dealId(1L).dealActionStateId(7L).build();
    }

    private static DealActionState anchor(SystemActionType type, Long trancheId) {
        DealActionState state = new DealActionState();
        state.setId(7L);
        state.setDealId(1L);
        state.setActionKind(ActionKind.SYSTEM);
        state.setSystemActionType(type);
        state.setDealTrancheId(trancheId);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }

    private static DealTranche tranche(DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setStatus(status);
        tranche.setEpisodeSeq(1);
        return tranche;
    }

    private static Deal dealWith(DealTranche tranche) {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setTranches(List.of(tranche));
        deal.setPositions(List.of());
        return deal;
    }

    private static Deal enteredDeal(Position episode) {
        Deal deal = dealWith(tranche(DealTranche.Status.CLOSED));
        deal.setStatus(Deal.Status.EXIT_PENDING);
        deal.setEntryReason(Deal.EntryReason.RECOVERY);
        deal.setPositions(List.of(episode));
        deal.setCoverageProvenThrough(OffsetDateTime.parse("2026-09-06T10:00:00Z"));
        deal.setBillsFetchedThrough(OffsetDateTime.parse("2026-09-06T11:00:00Z"));
        return deal;
    }

    private static Position closedEpisode(BigDecimal net) {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        episode.setExternalRealizedProfit(net);
        episode.setExternalResultCurrency(SETTLE);
        episode.setExternalCloseType("1");
        return episode;
    }

    private static Position episodeWithoutCloseRecord() {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        return episode;
    }

    private static DealContext context(Deal deal) {
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
                .actionStates(new ArrayList<>())
                .cashFlows(new ArrayList<>())
                .graphComplete(true)
                .flowsComplete(true)
                .computationAllowed(true)
                .build();
    }
}
