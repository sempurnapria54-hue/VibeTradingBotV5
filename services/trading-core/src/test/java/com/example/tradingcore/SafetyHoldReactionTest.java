package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.KillSwitchService;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.util.Constants;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Реакция ступени: что делает вызов и чем поглощённый вызов отличается от
 * применившегося (docs/components/HoldService.md,
 * docs/components/SafetyHoldCoordinator.md).
 *
 * <p><b>Проверяется наблюдаемость и порядок, а не факт вызова.</b> Три
 * места, где реакция ломается молча: строка журнала, погашенная гардом
 * перехода (контур стои́т, а почему — в данных нет); снимок «до»,
 * собранный после снятия риска (описывает не то состояние); повторный
 * прогон снятия риска по стоящей ступени. Каждый случай ниже — состояние,
 * на котором наивная реализация ответила бы «всё сделано».
 */
class SafetyHoldReactionTest {

    private static final Long ACCOUNT_ID = 4L;
    private static final Long INSTRUMENT_ID = 9L;
    private static final String CODE = "TEST_REASON";

    private final AccountInstrumentStateDataService pairStates = mock(AccountInstrumentStateDataService.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final AnomalyReportService reports = mock(AnomalyReportService.class);
    private final KillSwitchService killSwitchService = mock(KillSwitchService.class);
    private final DealDataService deals = mock(DealDataService.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);

    private SafetyHoldCoordinator coordinator;
    private HoldService holdService;

    @BeforeEach
    void setUp() {
        coordinator = new SafetyHoldCoordinator(pairStates, accounts, reports, killSwitchService, deals);
        holdService = new HoldService(pairStates, accounts, reports, coordinator, outboxWriter);
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        when(accounts.raiseRung(anyLong(), any())).thenReturn(true);
        when(reports.open(any(), any())).thenReturn(report());
        when(killSwitchService.fireInstrument(any())).thenReturn(true);
        when(killSwitchService.fireExchangeAccount(anyLong())).thenReturn(true);
    }

    @Test
    void softInstrumentSignalWritesJournalBeforeTheTransitionGuard() {
        holdService.raise(HoldSignal.instrumentSoft(CODE), context());

        InOrder order = inOrder(reports, pairStates);
        order.verify(reports).journalState(any(), any(), eq(null));
        order.verify(pairStates).raiseRung(ACCOUNT_ID, INSTRUMENT_ID, Instrument.SafetyRung.ENTRY_BLOCKED);
        verify(accounts, never()).raiseRung(anyLong(), any());
    }

    @Test
    void softInstrumentSignalAbsorbedByAStandingRungStillLeavesItsRow() {
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(false);

        holdService.raise(HoldSignal.instrumentSoft(CODE), context());

        verify(reports).journalState(any(), any(), eq(null));
    }

    @Test
    void softAccountSignalRaisesTheAccountRungAndLeavesThePairAlone() {
        holdService.raise(HoldSignal.exchangeAccountSoft(CODE), context());

        verify(accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.HOLD);
        verify(pairStates, never()).raiseRung(anyLong(), anyLong(), any());
    }

    @Test
    void aFailedJournalWriteDoesNotCancelTheEntryBlock() {
        when(reports.journalState(any(), any(), eq(null)))
                .thenThrow(new IllegalStateException("journal is down"));

        holdService.raise(HoldSignal.instrumentSoft(CODE), context());

        verify(pairStates).raiseRung(ACCOUNT_ID, INSTRUMENT_ID, Instrument.SafetyRung.ENTRY_BLOCKED);
    }

    @Test
    void hardSignalRunsTheStatusFirstAndOpensTheReportBeforeTearingDownRisk() {
        DealContext dealContext = context();

        holdService.raise(HoldSignal.instrument(CODE), dealContext);

        InOrder order = inOrder(pairStates, reports, killSwitchService, deals);
        order.verify(pairStates).raiseRung(ACCOUNT_ID, INSTRUMENT_ID, Instrument.SafetyRung.TRADE_BLOCKED);
        order.verify(reports).open(eq(dealContext), any());
        order.verify(reports).advance(any(), eq(AnomalyReport.Status.IN_PROGRESS));
        order.verify(killSwitchService).fireInstrument(dealContext);
        order.verify(reports).advance(any(), eq(AnomalyReport.Status.KILL_SWITCH_EXECUTED));
        order.verify(reports).complete(any(), eq(dealContext));
        order.verify(deals).cascadeInstrumentToError(ACCOUNT_ID, INSTRUMENT_ID);
    }

    @Test
    void hardSignalAbsorbedByAStandingRungDoesNotTearDownRiskAgainButLeavesItsRow() {
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(false);

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(killSwitchService, never()).fireInstrument(any());
        verify(deals, never()).cascadeInstrumentToError(anyLong(), anyLong());
        verify(reports).journalState(any(), any(), eq(null));
    }

    @Test
    void unconfirmedInstrumentTeardownEscalatesToTheAccountRadius() {
        when(killSwitchService.fireInstrument(any())).thenReturn(false);

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(killSwitchService).fireExchangeAccount(ACCOUNT_ID);
        verify(reports).open(any(), signalWithCode(Constants.Hold.EXCHANGE_KILL_SWITCH_RESIDUAL));
    }

    @Test
    void unconfirmedAccountTeardownKeepsTheReportOpenAndDoesNotEscalateFurther() {
        when(killSwitchService.fireExchangeAccount(anyLong())).thenReturn(false);

        holdService.raise(HoldSignal.exchangeAccount(CODE), context());

        verify(reports, never()).complete(any(), any());
        verify(killSwitchService, times(1)).fireExchangeAccount(ACCOUNT_ID);
        verify(deals).cascadeAccountToError(ACCOUNT_ID);
    }

    @Test
    void aTeardownFailureIsRecordedInTheReportAndDoesNotEscapeThePass() {
        when(killSwitchService.fireInstrument(any())).thenThrow(new IllegalStateException("exchange is down"));

        assertThatCode(() -> holdService.raise(HoldSignal.instrument(CODE), context()))
                .doesNotThrowAnyException();

        verify(reports).fail(any(), eq("exchange is down"));
        verify(reports, never()).complete(any(), any());
    }

    @Test
    void aFailedReportOpenDoesNotSuppressTheTeardown() {
        when(reports.open(any(), any())).thenThrow(new IllegalStateException("journal is down"));
        DealContext dealContext = context();

        holdService.raise(HoldSignal.instrument(CODE), dealContext);

        verify(killSwitchService).fireInstrument(dealContext);
        verify(deals).cascadeInstrumentToError(ACCOUNT_ID, INSTRUMENT_ID);
    }

    @Test
    void anEmptySignalIsNotASilentTeardownRequest() {
        holdService.raise(null, context());

        verify(pairStates, never()).raiseRung(anyLong(), anyLong(), any());
        verify(accounts, never()).raiseRung(anyLong(), any());
        verify(killSwitchService, never()).fireInstrument(any());
    }

    /** Сигнал с названным машинным кодом причины. */
    private static HoldSignal signalWithCode(String code) {
        return argThat(signal -> Objects.equals(code, signal.getCode()));
    }

    private static AnomalyReport report() {
        AnomalyReport report = new AnomalyReport();
        report.setId(1L);
        return report;
    }

    private static DealContext context() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId("ea-test-0001");
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId("ETH-USDT-SWAP");
        Deal deal = new Deal();
        deal.setId(77L);
        deal.setStatus(Deal.Status.ACTIVE);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .build();
    }
}
