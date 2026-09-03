package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.config.ExchangeContourProperties;
import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.other.external_snapshot.DealCashFlowExternalSnapshot;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.candle.external_snapshot.CandleExternalSnapshot;
import com.example.tradingbot.domain.safety.AnomalyReportService;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.DealCashFlowMapper;
import com.example.tradingbot.mapping.TimeFrameMapper;
import com.example.tradingbot.persistence.service.AnomalyReportDataService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.DealCashFlowDataService;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REFRESH_BILLS: строки окна персистятся с категорией по отображению
 * контура, линкуются предикатом окна, получают курс лестницей огрубления;
 * тип вне отображения садится в OTHER с одним журнальным отчётом; строка
 * блокирующей области без курса не даёт звену завершиться
 * (docs/components/RefreshBillsExecutor.md, docs/spec/deal-result.json
 * §rateBlocking, docs/spec/cash-flow-linkage.json).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshBillsExecutorTest {

    private static final Long DEAL_ID = 1L;
    private static final Long EXCHANGE_ID = 5L;
    private static final String INST_ID = "ETH-USDT-SWAP";
    private static final OffsetDateTime WINDOW_BEGIN =
            OffsetDateTime.of(2026, 9, 2, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SOURCE_TIME =
            OffsetDateTime.of(2026, 9, 2, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime IN_WINDOW =
            OffsetDateTime.of(2026, 9, 2, 11, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private DealDataService dealDataService;

    @Mock
    private DealCashFlowDataService dealCashFlowDataService;

    @Mock
    private DealActionStateDataService dealActionStateDataService;

    @Mock
    private InstrumentDataService instrumentDataService;

    @Mock
    private IntegrationService integrationService;

    @Mock
    private AnomalyReportService anomalyReportService;

    @Mock
    private AnomalyReportDataService anomalyReportDataService;

    @Spy
    private ExchangeContourProperties exchangeContourProperties = contourProperties();

    @InjectMocks
    private RefreshBillsExecutor executor;

    private final List<DealCashFlow> savedFlows = new ArrayList<>();
    private Deal deal;
    private DealContext dealContext;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(executor, "dealCashFlowMapper", realMapper());
        ReflectionTestUtils.setField(executor, "timeFrameMapper", new TimeFrameMapper() {
        });
        deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setBillsWindowBegin(WINDOW_BEGIN);
        Instrument instrument = new Instrument();
        instrument.setId(2L);
        instrument.setExternalId(INST_ID);
        instrument.setExternalSettlementCurrency("USDT");
        Exchange exchange = new Exchange();
        exchange.setId(EXCHANGE_ID);
        exchange.setName("OKX");
        dealContext = DealContext.builder().deal(deal).instrument(instrument).exchange(exchange).build();

        savedFlows.clear();
        when(integrationService.getServerTime()).thenReturn(SOURCE_TIME);
        when(integrationService.getBills(any(), any())).thenReturn(List.of());
        when(integrationService.getBillsArchive(any(), any())).thenReturn(List.of());
        when(dealCashFlowDataService.exists(any(), anyString())).thenReturn(false);
        when(dealCashFlowDataService.save(any())).thenAnswer(invocation -> {
            DealCashFlow flow = invocation.getArgument(0);
            if (isNull(flow.getId())) {
                flow.setId(savedFlows.size() + 1L);
                savedFlows.add(flow);
            }
            return flow;
        });
        when(dealCashFlowDataService.findByDeal(DEAL_ID)).thenAnswer(invocation -> savedFlows.stream()
                .filter(flow -> Objects.equals(DEAL_ID, flow.getDealId()))
                .collect(Collectors.toList()));
        when(anomalyReportDataService.existsByKey(any(), anyString(), any())).thenReturn(false);
    }

    @Test
    void linkedRowGetsCategoryWindowAndNotRequiredRate() {
        when(integrationService.getBills(any(), any()))
                .thenReturn(List.of(bill("b1", "2", "1", "USDT", INST_ID, IN_WINDOW)));

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(savedFlows).hasSize(1);
        DealCashFlow flow = savedFlows.get(0);
        assertThat(flow.getDealId()).isEqualTo(DEAL_ID);
        assertThat(flow.getExchangeId()).isEqualTo(EXCHANGE_ID);
        assertThat(flow.getCategory()).isEqualTo(DealCashFlow.CashFlowCategory.REALIZED_PNL);
        assertThat(flow.getRateStatus()).isEqualTo(DealCashFlow.RateStatus.NOT_REQUIRED);
        verify(dealDataService).advanceBillsFetchedThrough(DEAL_ID, SOURCE_TIME);
    }

    @Test
    void rowOutsideWindowOrForeignInstrumentStaysUnlinked() {
        when(instrumentDataService.findSettlementCurrency(eq(EXCHANGE_ID), anyString()))
                .thenReturn(Optional.of("USDT"));
        when(integrationService.getBills(any(), any())).thenReturn(List.of(
                bill("b1", "2", "1", "USDT", INST_ID, WINDOW_BEGIN.minusMinutes(5)),
                bill("b2", "2", "1", "USDT", "BTC-USDT-SWAP", IN_WINDOW)));

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(savedFlows).hasSize(2);
        assertThat(savedFlows).allMatch(flow -> isNull(flow.getDealId()));
    }

    @Test
    void unmappedTypeFallsToOtherWithSingleJournalReport() {
        when(anomalyReportDataService.existsByKey(any(), anyString(), any()))
                .thenReturn(false)
                .thenReturn(true);
        when(integrationService.getBills(any(), any())).thenReturn(List.of(
                bill("b1", "1", "11", "USDT", null, IN_WINDOW),
                bill("b2", "1", "12", "USDT", null, IN_WINDOW)));

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(savedFlows).allMatch(flow ->
                Objects.equals(DealCashFlow.CashFlowCategory.OTHER, flow.getCategory()));
        verify(anomalyReportService, times(1)).journal(eq(dealContext), any());
    }

    @Test
    void crossCurrencyRowTakesSecondResolutionFirst() {
        when(integrationService.getBills(any(), any()))
                .thenReturn(List.of(bill("b1", "2", "1", "BTC", INST_ID, IN_WINDOW)));
        when(integrationService.getIndexCandleAt(eq("BTC-USDT"), eq("1s"), any()))
                .thenReturn(candle(IN_WINDOW, "77000.5"));

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        DealCashFlow flow = savedFlows.get(0);
        assertThat(flow.getRateStatus()).isEqualTo(DealCashFlow.RateStatus.APPLIED);
        assertThat(flow.getAppliedRate()).isEqualByComparingTo(new BigDecimal("77000.5"));
        assertThat(flow.getAppliedRateCandleTimeframe()).isEqualTo(TimeFrame.ONE_SECOND);
        assertThat(flow.getAppliedRateCandleInstrument()).isEqualTo("BTC-USDT");
        assertThat(flow.getAppliedRateCandleOpenTime()).isEqualTo(IN_WINDOW);
    }

    @Test
    void ladderDegradesToMinuteCandleAndRecordsResolution() {
        when(integrationService.getBills(any(), any()))
                .thenReturn(List.of(bill("b1", "2", "1", "BTC", INST_ID, IN_WINDOW.plusSeconds(30))));
        when(integrationService.getIndexCandleAt(eq("BTC-USDT"), eq("1s"), any())).thenReturn(null);
        when(integrationService.getIndexCandleAt(eq("BTC-USDT"), eq("1m"), any()))
                .thenReturn(candle(IN_WINDOW, "77000.5"));

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(savedFlows.get(0).getRateStatus()).isEqualTo(DealCashFlow.RateStatus.APPLIED);
        assertThat(savedFlows.get(0).getAppliedRateCandleTimeframe()).isEqualTo(TimeFrame.ONE_MINUTE);
    }

    @Test
    void candleNotCoveringMomentIsNotApplied() {
        // Свеча открыта минутой раньше момента: секундная её не накрывает,
        // минутная (той же меткой открытия) — накрывает.
        OffsetDateTime moment = IN_WINDOW.plusSeconds(59);
        when(integrationService.getBills(any(), any()))
                .thenReturn(List.of(bill("b1", "2", "1", "BTC", INST_ID, moment)));
        when(integrationService.getIndexCandleAt(eq("BTC-USDT"), eq("1s"), any()))
                .thenReturn(candle(IN_WINDOW, "76000"));
        when(integrationService.getIndexCandleAt(eq("BTC-USDT"), eq("1m"), any()))
                .thenReturn(candle(IN_WINDOW, "77000"));

        executor.execute(command(), new DealActionState(), dealContext);

        assertThat(savedFlows.get(0).getAppliedRateCandleTimeframe()).isEqualTo(TimeFrame.ONE_MINUTE);
        assertThat(savedFlows.get(0).getAppliedRate()).isEqualByComparingTo(new BigDecimal("77000"));
    }

    @Test
    void unresolvedRateOnLinkedRowBlocksCompletion() {
        when(integrationService.getBills(any(), any()))
                .thenReturn(List.of(bill("b1", "2", "1", "BTC", INST_ID, IN_WINDOW)));
        when(integrationService.getIndexCandleAt(anyString(), anyString(), any())).thenReturn(null);
        DealActionState actionState = new DealActionState();

        ServiceCommandExecutionResult result = executor.execute(command(), actionState, dealContext);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.EXCHANGE_ERROR);
        assertThat(savedFlows.get(0).getRateStatus()).isEqualTo(DealCashFlow.RateStatus.RATE_UNAVAILABLE);
        assertThat(actionState.getStatus()).isNotEqualTo(DealActionStateStatus.COMPLETED);
        verify(dealDataService).advanceBillsFetchedThrough(DEAL_ID, SOURCE_TIME);
    }

    @Test
    void pendingRateFromPriorPassIsRetriedThisPass() {
        DealCashFlow stale = new DealCashFlow();
        stale.setId(100L);
        stale.setDealId(DEAL_ID);
        stale.setExchangeId(EXCHANGE_ID);
        stale.setCcy("BTC");
        stale.setExternalInstrumentId(INST_ID);
        stale.setExternalType("2");
        stale.setExternalCreatedAt(IN_WINDOW);
        stale.setRateStatus(DealCashFlow.RateStatus.RATE_UNAVAILABLE);
        when(dealCashFlowDataService.findByDeal(DEAL_ID)).thenReturn(List.of(stale));
        when(integrationService.getIndexCandleAt(eq("BTC-USDT"), eq("1s"), any()))
                .thenReturn(candle(IN_WINDOW, "77000"));

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(stale.getRateStatus()).isEqualTo(DealCashFlow.RateStatus.APPLIED);
        verify(dealCashFlowDataService).save(stale);
    }

    @Test
    void duplicateBillIsNotSavedAgain() {
        when(dealCashFlowDataService.exists(EXCHANGE_ID, "b1")).thenReturn(true);
        when(integrationService.getBills(any(), any()))
                .thenReturn(List.of(bill("b1", "2", "1", "USDT", INST_ID, IN_WINDOW)));

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(savedFlows).isEmpty();
    }

    @Test
    void archiveJoinsConveyorOnlyForDeepWindow() {
        executor.execute(command(), new DealActionState(), dealContext);
        verify(integrationService, never()).getBillsArchive(any(), any());

        deal.setBillsWindowBegin(SOURCE_TIME.minusDays(10));
        executor.execute(command(), new DealActionState(), dealContext);
        verify(integrationService).getBillsArchive(SOURCE_TIME.minusDays(10), SOURCE_TIME);
    }

    @Test
    void unaddressableWindowFailsWithoutFetch() {
        deal.setBillsWindowBegin(null);
        deal.setExternalCreatedAt(null);

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(RuntimeErrorCode.VALIDATION_ERROR);
        verify(integrationService, never()).getBills(any(), any());
    }

    @Test
    void excludedTypeWithoutRateDoesNotBlock() {
        when(integrationService.getBills(any(), any()))
                .thenReturn(List.of(bill("b1", "2", "407", "BTC", INST_ID, IN_WINDOW)));
        when(integrationService.getIndexCandleAt(anyString(), anyString(), any())).thenReturn(null);

        ServiceCommandExecutionResult result = executor.execute(command(), new DealActionState(), dealContext);

        assertThat(result.getSuccess()).isTrue();
        assertThat(savedFlows.get(0).getRateStatus()).isEqualTo(DealCashFlow.RateStatus.RATE_UNAVAILABLE);
    }

    // ------------------------------------------------------------------
    // Фикстуры
    // ------------------------------------------------------------------

    private ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_BILLS)
                .dealId(DEAL_ID)
                .build();
    }

    private DealCashFlowExternalSnapshot bill(String billId, String type, String subType, String ccy,
                                              String instId, OffsetDateTime at) {
        return DealCashFlowExternalSnapshot.builder()
                .externalBillId(billId)
                .amount(new BigDecimal("1.5"))
                .ccy(ccy)
                .externalType(type)
                .externalSubType(subType)
                .externalInstrumentId(instId)
                .externalCreatedAt(at)
                .build();
    }

    private CandleExternalSnapshot candle(OffsetDateTime openAt, String close) {
        return CandleExternalSnapshot.builder()
                .openTimestamp(openAt.toInstant().toEpochMilli())
                .open(new BigDecimal(close))
                .high(new BigDecimal(close))
                .low(new BigDecimal(close))
                .close(new BigDecimal(close))
                .confirm(true)
                .build();
    }

    private static ExchangeContourProperties contourProperties() {
        ExchangeContourProperties properties = new ExchangeContourProperties();
        ExchangeContourProperties.Contour contour = new ExchangeContourProperties.Contour();
        contour.getCashFlowCategoryMapping().put("2", DealCashFlow.CashFlowCategory.REALIZED_PNL);
        contour.getCashFlowCategoryMapping().put("8", DealCashFlow.CashFlowCategory.FUNDING);
        contour.getCashFlowCategoryMapping().put("5/116", DealCashFlow.CashFlowCategory.LIQ_PENALTY);
        contour.getReconciliationExclusions().add("2/407");
        properties.setExchanges(Map.of("OKX", contour));
        return properties;
    }

    private DealCashFlowMapper realMapper() {
        try {
            Class<?> impl = Class.forName("com.example.tradingbot.mapping.DealCashFlowMapperImpl");
            Object mapper = impl.getDeclaredConstructor().newInstance();
            ReflectionTestUtils.setField(mapper, "okxResponseConverter",
                    new com.example.tradingbot.mapping.OkxResponseConverter());
            return (DealCashFlowMapper) mapper;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("DealCashFlowMapperImpl не сгенерирован", e);
        }
    }
}
