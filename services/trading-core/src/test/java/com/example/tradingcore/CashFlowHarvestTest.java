package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.executor.RefreshBillsExecutor;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealCashFlowDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Добыча движений средств: конвейер окна, категория, линковка и лестница
 * огрубления курса.
 *
 * <p><b>Предмет — направление ошибки на пустых операндах.</b> Курс
 * пустотой не подменяется: нерезолвленная расчётная валюта и ненайденная
 * свеча дают РАЗНЫЕ значения признака, и оба блокируют итог. Прочтение
 * любого из них как «курс не нужен» сняло бы блокировку и опубликовало
 * итог по неполным данным.
 *
 * <p><b>Второй предмет — что «не добыто» не отказ.</b> Площадка ответила и
 * повода для радиусной реакции не дала: звено не завершается и
 * повторяется по бюджету своей строки.
 */
class CashFlowHarvestTest {

    private static final Long DEAL_ID = 5L;
    private static final Long ACCOUNT_ID = 3L;
    private static final String ACCOUNT = "ea-0001";
    private static final String EXCHANGE_CODE = "OKX";
    private static final String INSTRUMENT = "BTC-USDT-SWAP";
    private static final String SETTLE = "USDT";
    private static final OffsetDateTime WINDOW_BEGIN = OffsetDateTime.parse("2026-09-01T10:00:00Z");
    private static final OffsetDateTime EVENT_AT = OffsetDateTime.parse("2026-09-01T11:00:00Z");
    private static final OffsetDateTime SOURCE_TIME = OffsetDateTime.parse("2026-09-01T12:00:00Z");

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealCashFlowDataService cashFlowDataService = mock(DealCashFlowDataService.class);
    private final DealActionStateDataService actionStateDataService = mock(DealActionStateDataService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final AnomalyReportService anomalyReportService = mock(AnomalyReportService.class);
    private final ExchangeContourProperties contourProperties = contourProperties();

    private final List<DealCashFlow> stored = new ArrayList<>();

    private final RefreshBillsExecutor executor = new RefreshBillsExecutor(dealDataService, cashFlowDataService,
            actionStateDataService, instrumentDataService, exchange, contourProperties, anomalyReportService);

    /**
     * Строка расчётной валюты курса не требует, категория берётся из
     * отображения контура, линковка ставит сделку — и звено завершается.
     */
    @Test
    void aSettlementCurrencyRowNeedsNoRateAndLinksToTheDeal() {
        givenPipeline(flow("bill-1", "2", null, SETTLE, "12.5"));

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal()));

        DealCashFlow saved = savedFlows().getFirst();
        assertThat(saved.getCategory()).isEqualTo(DealCashFlow.CashFlowCategory.REALIZED_PNL);
        assertThat(saved.getRateStatus()).isEqualTo(DealCashFlow.RateStatus.NOT_REQUIRED);
        assertThat(saved.getDealId()).isEqualTo(DEAL_ID);
        assertThat(result.getSuccess()).isTrue();
        verify(exchange, never()).getIndexCandleAt(any(), any(), any());
    }

    /**
     * Секундная свеча выигрывает у минутной, и применённое разрешение
     * ЗАПИСЫВАЕТСЯ: иначе постфактум не отличить точный пересчёт от
     * огрублённого.
     */
    @Test
    void theFinestAvailableResolutionWinsAndIsRecorded() {
        givenPipeline(flow("bill-1", "2", null, "BTC", "0.01"));
        when(exchange.getIndexCandleAt("BTC-USDT", TimeFrame.ONE_SECOND, EVENT_AT))
                .thenReturn(candle(EVENT_AT, "60000"));

        executor.execute(command(), row(), context(deal()));

        DealCashFlow saved = savedFlows().getFirst();
        assertThat(saved.getRateStatus()).isEqualTo(DealCashFlow.RateStatus.APPLIED);
        assertThat(saved.getAppliedRate()).isEqualByComparingTo("60000");
        assertThat(saved.getAppliedRateCandleTimeframe()).isEqualTo(TimeFrame.ONE_SECOND);
        verify(exchange, never()).getIndexCandleAt(any(), eq(TimeFrame.ONE_MINUTE), any());
    }

    /**
     * Огрубление не прячется: секундной свечи нет — применяется минутная,
     * и координата таймфрейма это показывает.
     */
    @Test
    void aCoarserResolutionIsVisibleInTheRecordedCoordinate() {
        givenPipeline(flow("bill-1", "2", null, "BTC", "0.01"));
        when(exchange.getIndexCandleAt("BTC-USDT", TimeFrame.ONE_SECOND, EVENT_AT)).thenReturn(null);
        when(exchange.getIndexCandleAt("BTC-USDT", TimeFrame.ONE_MINUTE, EVENT_AT))
                .thenReturn(candle(EVENT_AT, "59900"));

        executor.execute(command(), row(), context(deal()));

        DealCashFlow saved = savedFlows().getFirst();
        assertThat(saved.getRateStatus()).isEqualTo(DealCashFlow.RateStatus.APPLIED);
        assertThat(saved.getAppliedRateCandleTimeframe()).isEqualTo(TimeFrame.ONE_MINUTE);
    }

    /**
     * Свеча, закончившаяся ДО момента операции, ступень не закрывает:
     * источник отдаёт ближайшую доступную, и без проверки накрытия курс
     * приехал бы с чужого бара.
     */
    @Test
    void aCandleThatEndedBeforeTheMomentDoesNotSatisfyTheRung() {
        givenPipeline(flow("bill-1", "2", null, "BTC", "0.01"));
        when(exchange.getIndexCandleAt(any(), any(), any())).thenReturn(candle(EVENT_AT.minusMinutes(5), "1"));

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal()));

        assertThat(savedFlows().getFirst().getRateStatus()).isEqualTo(DealCashFlow.RateStatus.RATE_UNAVAILABLE);
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isNull();
    }

    /**
     * Нерезолвленная расчётная валюта — СВОЁ значение признака, а не «курс
     * не нужен»: курс при ней не ищется вовсе, а итог остаётся
     * заблокированным.
     */
    @Test
    void anUnresolvedSettlementCurrencyIsItsOwnValueAndBlocks() {
        givenPipeline(flow("bill-1", "2", null, "BTC", "0.01"));
        Instrument instrument = new Instrument();
        instrument.setExternalId("OTHER-SWAP");
        instrument.setExternalSettlementCurrency(SETTLE);
        when(instrumentDataService.findSettlementCurrency(EXCHANGE_CODE, INSTRUMENT))
                .thenReturn(Optional.empty());

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal(), instrument));

        assertThat(savedFlows().getFirst().getRateStatus())
                .isEqualTo(DealCashFlow.RateStatus.SETTLE_CURRENCY_UNAVAILABLE);
        assertThat(result.getSuccess()).isFalse();
        verify(exchange, never()).getIndexCandleAt(any(), any(), any());
    }

    /**
     * Строка, выведенная из области сверки списком исключений, итог не
     * блокирует, хотя курса у неё и нет: область блокировки у́же множества
     * строк без курса.
     */
    @Test
    void anExcludedRowWithoutARateDoesNotBlockTheResult() {
        DealCashFlow excluded = flow("bill-1", "2", "407", "BTC", "0.01");
        givenPipeline(excluded);
        when(exchange.getIndexCandleAt(any(), any(), any())).thenReturn(null);

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal()));

        assertThat(savedFlows().getFirst().getRateStatus()).isEqualTo(DealCashFlow.RateStatus.RATE_UNAVAILABLE);
        assertThat(result.getSuccess()).isTrue();
    }

    /**
     * Тип вне отображения садится в принимающую корзину и заводит
     * журнальный отчёт — состояние возникло. Отчёт объявляется на
     * ВОЗНИКНОВЕНИЕ: стояла бы корзина непустой, второй строки бы не было.
     */
    @Test
    void anUnmappedTypeFallsIntoTheBasketAndJournalsTheState() {
        givenPipeline(flow("bill-1", "999", null, SETTLE, "1"));

        executor.execute(command(), row(), context(deal()));

        assertThat(savedFlows().getFirst().getCategory()).isEqualTo(DealCashFlow.CashFlowCategory.OTHER);
        ArgumentCaptor<HoldSignal> signal = ArgumentCaptor.forClass(HoldSignal.class);
        verify(anomalyReportService).journalState(any(), signal.capture(), eq(null));
        assertThat(signal.getValue().getCode()).isEqualTo("UNCLASSIFIED_CASH_FLOW");
    }

    /**
     * Корзина, стоявшая непустой, второй строки журнала не порождает:
     * состояние держится, а не возникает заново.
     */
    @Test
    void aStandingBasketDoesNotJournalTheStateAgain() {
        givenPipeline(flow("bill-1", "999", null, SETTLE, "1"));
        when(cashFlowDataService.unclassifiedBasketStands(ACCOUNT_ID)).thenReturn(true);

        executor.execute(command(), row(), context(deal()));

        verify(anomalyReportService, never()).journalState(any(), any(), any());
    }

    /**
     * Запись, уже приземлённая по ключу идемпотентности, второй строки не
     * порождает: повторный проход перечитывает окно целиком.
     */
    @Test
    void anAlreadyLandedRecordIsNotStoredTwice() {
        givenPipeline(flow("bill-1", "2", null, SETTLE, "1"));
        when(cashFlowDataService.exists(ACCOUNT_ID, "bill-1")).thenReturn(true);

        executor.execute(command(), row(), context(deal()));

        verify(cashFlowDataService, never()).save(any());
    }

    /**
     * Архив зовётся, только когда нижняя граница окна старше глубины
     * свежего эндпоинта: иначе это платный вызов за уже добытое.
     */
    @Test
    void theArchiveIsCalledOnlyWhenTheWindowOutrunsTheFreshDepth() {
        givenPipeline(flow("bill-1", "2", null, SETTLE, "1"));

        executor.execute(command(), row(), context(deal()));
        verify(exchange, never()).getBillsArchive(any(), any(), any());

        Deal old = deal();
        old.setBillsWindowBegin(SOURCE_TIME.minusDays(30));
        when(exchange.getBills(eq(ACCOUNT), any(), eq(SOURCE_TIME))).thenReturn(List.of());
        when(exchange.getBillsArchive(eq(ACCOUNT), any(), eq(SOURCE_TIME))).thenReturn(List.of());

        executor.execute(command(), row(), context(old));
        verify(exchange).getBillsArchive(eq(ACCOUNT), any(), eq(SOURCE_TIME));
    }

    /**
     * Окно неадресуемо — ни границы, ни суррогата: это НАШ дефект (писатель
     * границы не отработал), а не недобытый факт, и звено отказывает
     * классифицированно.
     */
    @Test
    void anUnaddressableWindowIsOurDefectNotAMissingFact() {
        Deal deal = deal();
        deal.setBillsWindowBegin(null);
        deal.setExternalCreatedAt(null);

        ServiceCommandExecutionResult result = executor.execute(command(), row(), context(deal));

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isNotNull();
        verify(exchange, never()).getBills(any(), any(), any());
    }

    private void givenPipeline(DealCashFlow... fetched) {
        AtomicLong ids = new AtomicLong(100L);
        when(exchange.getServerTime()).thenReturn(SOURCE_TIME);
        when(exchange.getBills(eq(ACCOUNT), any(), eq(SOURCE_TIME))).thenReturn(List.of(fetched));
        when(exchange.getBillsArchive(eq(ACCOUNT), any(), eq(SOURCE_TIME))).thenReturn(List.of());
        when(cashFlowDataService.findUnclassifiedByDeal(DEAL_ID)).thenReturn(List.of());
        when(cashFlowDataService.unclassifiedBasketStands(ACCOUNT_ID)).thenReturn(false);
        when(cashFlowDataService.exists(any(), any())).thenReturn(false);
        when(cashFlowDataService.save(any())).thenAnswer(call -> {
            DealCashFlow flow = call.getArgument(0);
            if (isNull(flow.getId())) {
                flow.setId(ids.incrementAndGet());
                stored.add(flow);
            }
            return flow;
        });
        when(cashFlowDataService.findByDeal(DEAL_ID)).thenReturn(stored);
    }

    /** Приземлённые строки — в порядке приземления; пусто, если ни одной. */
    private List<DealCashFlow> savedFlows() {
        return stored;
    }

    private static DealCashFlow flow(String billId, String type, String subType, String ccy, String amount) {
        DealCashFlow flow = new DealCashFlow();
        flow.setExternalBillId(billId);
        flow.setExternalType(type);
        flow.setExternalSubType(subType);
        flow.setCcy(ccy);
        flow.setAmount(new BigDecimal(amount));
        flow.setExternalInstrumentId(INSTRUMENT);
        flow.setExternalCreatedAt(EVENT_AT);
        return flow;
    }

    private static Candle candle(OffsetDateTime openAt, String close) {
        Candle candle = new Candle();
        candle.setOpenTimestamp(openAt.toInstant().toEpochMilli());
        candle.setClose(new BigDecimal(close));
        return candle;
    }

    private static ExchangeContourProperties contourProperties() {
        ExchangeContourProperties.Contour contour = new ExchangeContourProperties.Contour();
        contour.setCashFlowCategoryMapping(Map.of(
                "2", DealCashFlow.CashFlowCategory.REALIZED_PNL,
                "8", DealCashFlow.CashFlowCategory.FUNDING));
        contour.setReconciliationExclusions(List.of("2/407"));
        contour.setBillsFreshDepthDays(7);
        ExchangeContourProperties properties = new ExchangeContourProperties();
        properties.setExchanges(Map.of(EXCHANGE_CODE, contour));
        return properties;
    }

    private static Deal deal() {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setBillsWindowBegin(WINDOW_BEGIN);
        deal.setExternalCreatedAt(WINDOW_BEGIN);
        return deal;
    }

    private static DealContext context(Deal deal) {
        Instrument instrument = new Instrument();
        instrument.setId(9L);
        instrument.setExternalId(INSTRUMENT);
        instrument.setExternalSettlementCurrency(SETTLE);
        return context(deal, instrument);
    }

    private static DealContext context(Deal deal, Instrument instrument) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT);
        account.setExchangeCode(EXCHANGE_CODE);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .build();
    }

    private static ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_BILLS_COMMAND)
                .dealId(DEAL_ID)
                .dealActionStateId(42L)
                .build();
    }

    private static DealActionState row() {
        DealActionState row = new DealActionState();
        row.setId(42L);
        row.setDealId(DEAL_ID);
        row.setStatus(DealActionStateStatus.SUBMITTED);
        return row;
    }
}
