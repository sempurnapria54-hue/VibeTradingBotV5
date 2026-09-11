package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.config.ManualHaltProperties;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingcore.domain.event.OutboxWriter;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldRungEdgeService;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.ManualHaltClass;
import com.example.tradingcore.domain.safety.ManualHaltService;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.util.Constants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ручное управление остановкой: пары, отказы при запуске, доведение
 * недоделанного и цель снятия.
 *
 * <p><b>Что здесь проверяется по существу.</b> Снятие сворачивания поверх
 * непогашенного риска возвращает вход в торговлю над живым риском — это
 * разрешающая ошибка, а они запрещены. Снятие сворачивания сразу в
 * рабочее состояние отдаёт держателю торговлю одним нажатием вместо двух
 * осознанных. Повтор снятия, шагающий по лестнице дальше, возвращает
 * торговлю двойным нажатием. Полный вызов держателя, поглощённый анкером,
 * оставляет ступень сворачивания без выхода вовсе — ровно то, что
 * концепция называет недопустимым.
 */
class ManualHaltSurfaceTest {

    private static final Long ACCOUNT_ID = 2L;
    private static final Long INSTRUMENT_ID = 3L;
    private static final String ACCOUNT_INTERNAL_ID = "ea-0001";
    private static final String INSTRUMENT_INTERNAL_ID = "in-0001";

    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final InstrumentDataService instruments = mock(InstrumentDataService.class);
    private final AccountInstrumentStateDataService pairStates =
            mock(AccountInstrumentStateDataService.class);
    private final DealDataService deals = mock(DealDataService.class);
    private final DealContextService contexts = mock(DealContextService.class);
    private final SafetyHoldCoordinator coordinator = mock(SafetyHoldCoordinator.class);
    private final AnomalyReportService reports = mock(AnomalyReportService.class);

    private final ExchangeAccount account = account(ExchangeAccount.SafetyRung.ACTIVE);
    private final Instrument instrument = instrument(Instrument.Status.ACTIVE);

    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final HoldService holdService = new HoldService(reports, coordinator,
            new HoldRungEdgeService(pairStates, accounts, new ActorProvider(), outboxWriter));

    private final ManualHaltService service = new ManualHaltService(accounts, instruments, pairStates,
            deals, contexts, new DealTerminalGate(), coordinator, holdService, reports,
            new ManualHaltProperties());

    @BeforeEach
    void givenWorkingObjects() {
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account);
        when(instruments.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID)).thenReturn(instrument);
        when(pairStates.getRequiredByPair(ACCOUNT_ID, INSTRUMENT_ID))
                .thenReturn(pairState(Instrument.SafetyRung.ACTIVE));
        when(deals.findRiskCandidatesOnScope(anyLong(), any(), any())).thenReturn(List.of());
    }

    // --- допустимые пары ---------------------------------------------------

    /** Мягкий класс инструмента без названного инструмента — отказ при запуске. */
    @Test
    void theInstrumentSoftClassWithoutItsInstrumentIsRefused() {
        assertThatThrownBy(() -> service.raise(ManualHaltClass.SOFT, ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Мягкий класс счёта с названным инструментом — отказ при запуске. */
    @Test
    void theAccountSoftClassWithAnInstrumentIsRefused() {
        assertThatThrownBy(() -> service.raise(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID,
                INSTRUMENT_INTERNAL_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- постановка --------------------------------------------------------

    /** Мягкая постановка счёта: статус плюс строка журнала, координатор не зовётся. */
    @Test
    void theAccountSoftRaiseGoesWithoutTheCoordinator() {
        when(accounts.raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.HOLD)).thenReturn(true);

        service.raise(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.HOLD);
        verify(reports).journalState(any(), eq(new HoldSignal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.SOFT,
                Constants.Hold.MANUAL_HALT_REQUESTED)), eq(null));
        verify(coordinator, never()).react(any(), any(), any());
    }

    /**
     * Ручная постановка порождает факт подъёма ступени: событие пишет тот
     * код, который переставляет ступень, — иначе поднятая держателем
     * ступень не оставила бы в журнале класса, по которому её считают
     * (docs/rules/statistics-aggregates.md §«Счётчики происшествий — своё
     * зерно, а не строка сделочного агрегата»).
     */
    @Test
    void theManualRaisePublishesTheRaisedFact() {
        when(accounts.raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.HOLD)).thenReturn(true);

        service.raise(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(outboxWriter).write(eq("tn-0001"), eq(CoreEventType.HOLD_RAISED), any());
    }

    /**
     * Поглощённая ручная постановка события не производит: ступень уже
     * стои́т, статус не двигался, и объявлять фактом ход, которого не было,
     * нельзя.
     */
    @Test
    void anAbsorbedManualRaisePublishesNothing() {
        when(accounts.raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.HOLD)).thenReturn(false);

        service.raise(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(outboxWriter, never()).write(any(), any(), any());
    }

    /**
     * Мягкий класс на объекте вне рабочего состояния и без стоящей ступени
     * своего радиуса — отказ: множество входа мягкой ступени только
     * рабочее состояние.
     */
    @Test
    void theSoftRaiseOnANonWorkingObjectIsRefused() {
        when(instruments.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID))
                .thenReturn(instrument(Instrument.Status.CANDLES_LOADING));

        assertThatThrownBy(() -> service.raise(ManualHaltClass.SOFT, ACCOUNT_INTERNAL_ID,
                INSTRUMENT_INTERNAL_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Полный класс применяется из любого статуса: авария застаёт объект каким угодно. */
    @Test
    void theFullRaiseAppliesFromAnyStatus() {
        when(instruments.getRequiredByInternalId(INSTRUMENT_INTERNAL_ID))
                .thenReturn(instrument(Instrument.Status.CANDLES_LOADING));

        service.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, INSTRUMENT_INTERNAL_ID);

        verify(coordinator).react(eq(new HoldSignal(HoldScope.INSTRUMENT, HoldRung.HARD,
                Constants.Hold.MANUAL_HALT_REQUESTED)), any(DealContext.class));
    }

    /**
     * Повторный полный вызов держателя при НЕПОГАШЕННОМ риске доводит
     * недоделанное: анкер его не поглощает, и снятие риска гоняется
     * заново. Без этой тропы у ступени сворачивания не было бы выхода.
     */
    @Test
    void aHolderFullCallOverLiveRiskRetriesTheTeardown() {
        givenAccountRung(ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        givenLiveRiskOnScope();

        service.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(coordinator).react(any(), any(DealContext.class), eq(true));
    }

    /**
     * Тот же вызов при погашенном риске доведения не запрашивает: он идёт
     * общим исполнителем блокировки, а тот права на обход анкера не имеет.
     */
    @Test
    void aHolderFullCallOverClearedRiskDoesNotRetry() {
        givenAccountRung(ExchangeAccount.SafetyRung.TRADE_BLOCKED);

        service.raise(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(coordinator, never()).react(any(), any(DealContext.class), eq(true));
        verify(coordinator).react(any(), any(DealContext.class));
    }

    // --- снятие ------------------------------------------------------------

    /**
     * Снятие сворачивания счёта ведёт в МЯГКУЮ ступень, а не в рабочее
     * состояние: две ступени — два осознанных хода держателя.
     */
    @Test
    void clearingTheAccountTeardownLandsOnTheSoftRung() {
        givenAccountRung(ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        when(accounts.clearRung(any(), any(), any())).thenReturn(Boolean.TRUE);

        service.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null);

        verify(accounts).clearRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED,
                ExchangeAccount.SafetyRung.HOLD);
    }

    /** Снятие сворачивания поверх непогашенного живого риска — отказ при запуске. */
    @Test
    void clearingTheTeardownOverLiveRiskIsRefused() {
        givenAccountRung(ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        givenLiveRiskOnScope();

        assertThatThrownBy(() -> service.clear(ManualHaltClass.FULL, ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(accounts, never()).clearRung(any(), any(), any());
    }

    /** Вызов «снять мягкую» на объекте под сворачиванием — отказ: прыжка через ступень нет. */
    @Test
    void clearingTheSoftRungUnderTheHardOneIsRefused() {
        givenAccountRung(ExchangeAccount.SafetyRung.TRADE_BLOCKED);

        assertThatThrownBy(() -> service.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Названная ступень не стои́т — холостой ход: по лестнице снятие не
     * шагает, и строки журнала не заводит (ничего не произошло).
     */
    @Test
    void clearingAnAbsentRungIsANoop() {
        when(accounts.clearRung(any(), any(), any())).thenReturn(Boolean.FALSE);

        service.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(reports, never()).journal(any(), any());
    }

    /** Применённое снятие заводит строку журнала своим кодом — направление наблюдаемо. */
    @Test
    void anAppliedClearanceJournalsItsOwnCode() {
        when(accounts.clearRung(any(), any(), any())).thenReturn(Boolean.TRUE);

        service.clear(ManualHaltClass.FREEZE, ACCOUNT_INTERNAL_ID, null);

        verify(reports).journal(any(), eq(HoldSignal.exchangeAccountJournal(
                Constants.Hold.MANUAL_HALT_CLEARED)));
    }

    // --- сборка ------------------------------------------------------------

    private void givenAccountRung(ExchangeAccount.SafetyRung rung) {
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account(rung));
    }

    /** Нетерминальная сделка радиуса с живым риском: граф не предъявлен целиком. */
    private void givenLiveRiskOnScope() {
        Deal deal = new Deal();
        deal.setId(7L);
        deal.setStatus(Deal.Status.ERROR);
        deal.setTranches(new ArrayList<>());
        when(deals.findRiskCandidatesOnScope(anyLong(), any(), any()))
                .thenReturn(new ArrayList<>(List.of(deal)));
        when(contexts.build(deal)).thenReturn(DealContext.builder()
                .deal(deal)
                .graphComplete(Boolean.FALSE)
                .build());
    }

    private static ExchangeAccount account(ExchangeAccount.SafetyRung rung) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setTenantId("tn-0001");
        account.setStatus(ExchangeAccount.Status.ACTIVE);
        account.setSafetyRung(rung);
        return account;
    }

    private static Instrument instrument(Instrument.Status status) {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setInternalId(INSTRUMENT_INTERNAL_ID);
        instrument.setStatus(status);
        return instrument;
    }

    private static AccountInstrumentState pairState(Instrument.SafetyRung rung) {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setExchangeAccountId(ACCOUNT_ID);
        state.setInstrumentId(INSTRUMENT_ID);
        state.setSafetyRung(rung);
        return state;
    }
}
