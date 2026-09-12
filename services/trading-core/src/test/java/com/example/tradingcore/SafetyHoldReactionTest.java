package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
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
import com.example.tradingcore.domain.deal.DealStatusEdgeService;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.domain.service.ActorProvider;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HardRungShutdownReasonResolver;
import com.example.tradingcore.domain.safety.HoldRungEdgeService;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.safety.KillSwitchService;
import com.example.tradingcore.domain.safety.SafetyHoldCoordinator;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.util.Constants;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private final DealStatusEdgeService statusEdges = mock(DealStatusEdgeService.class);
    private final CoreEventWriter coreEventWriter = mock(CoreEventWriter.class);

    private SafetyHoldCoordinator coordinator;
    private HoldService holdService;

    @BeforeEach
    void setUp() {
        HoldRungEdgeService rungEdges = new HoldRungEdgeService(pairStates, accounts,
                new ActorProvider(), coreEventWriter);
        coordinator = new SafetyHoldCoordinator(reports, killSwitchService, deals, statusEdges, rungEdges,
                new HardRungShutdownReasonResolver(deals));
        holdService = new HoldService(reports, coordinator, rungEdges);
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        when(accounts.raiseRung(anyLong(), any())).thenReturn(true);
        when(reports.open(any(), any())).thenReturn(report());
        when(killSwitchService.fireInstrument(any())).thenReturn(true);
        when(killSwitchService.fireExchangeAccount(anyLong())).thenReturn(true);
        when(deals.findCascadeSourceOnPair(anyLong(), anyLong())).thenReturn(List.of(deal(77L)));
        when(deals.findCascadeSourceOnAccount(anyLong())).thenReturn(List.of(deal(77L)));
        instrumentRungStands();
    }

    /**
     * <b>Стоящая ступень — состояние, а не свойство сигнала.</b> Причину
     * остановки резолвит читатель по стоящим ступеням радиусов сделки
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»),
     * поэтому проба задаёт именно её, а не подменяет резолв.
     */
    private void accountRungStands() {
        when(deals.findIdsUnderAccountRung(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** То же на паре «счёт, инструмент». */
    private void instrumentRungStands() {
        when(deals.findIdsUnderInstrumentRung(any())).thenAnswer(invocation -> invocation.getArgument(0));
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

        InOrder order = inOrder(pairStates, reports, killSwitchService, statusEdges);
        order.verify(pairStates).raiseRung(ACCOUNT_ID, INSTRUMENT_ID, Instrument.SafetyRung.TRADE_BLOCKED);
        order.verify(reports).open(eq(dealContext), any());
        order.verify(reports).advance(any(), eq(AnomalyReport.Status.IN_PROGRESS));
        order.verify(killSwitchService).fireInstrument(dealContext);
        order.verify(reports).advance(any(), eq(AnomalyReport.Status.KILL_SWITCH_EXECUTED));
        order.verify(reports).complete(any(), eq(dealContext));
        order.verify(statusEdges).enforceHardRung(any(), eq(Deal.ShutdownReason.RISK_POLICY));
    }

    @Test
    void hardSignalAbsorbedByAStandingRungDoesNotTearDownRiskAgainButLeavesItsRow() {
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(false);

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(killSwitchService, never()).fireInstrument(any());
        verify(statusEdges, never()).enforceHardRung(any(), any());
        verify(reports).journalState(any(), any(), eq(null));
    }

    /**
     * <b>Факт подъёма пишется НА переходе ступени, а не над ним.</b>
     * Транзакцию проба не видит, но видит её следствие: строка outbox
     * ложится прежде любого внешнего вызова реакции — снятия риска и
     * каскада. Писатель, стоявший над реакцией, писал её последней, и
     * падение процесса в середине оставляло стоящую ступень без события
     * навсегда: повторный сигнал поглотил бы анкер той же ступени
     * (docs/architecture/contracts.md §«У каждого класса события назван
     * писатель, и он же писатель решения»).
     */
    @Test
    void theRaisedFactIsWrittenAtTheTransitionBeforeAnyExternalCall() {
        holdService.raise(HoldSignal.instrument(CODE), context());

        InOrder order = inOrder(pairStates, coreEventWriter, killSwitchService, statusEdges);
        order.verify(pairStates).raiseRung(ACCOUNT_ID, INSTRUMENT_ID, Instrument.SafetyRung.TRADE_BLOCKED);
        order.verify(coreEventWriter).holdRaised(any(), any(), any(), any(), any());
        order.verify(killSwitchService).fireInstrument(any());
        order.verify(statusEdges).enforceHardRung(any(), any());
    }

    /**
     * Поглощённый сигнал события не производит: статус не двигался, и
     * объявлять фактом ход, которого не было, нельзя.
     */
    @Test
    void anAbsorbedHardSignalWritesNoFact() {
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(false);

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(coreEventWriter, never()).holdRaised(any(), any(), any(), any(), any());
    }

    /**
     * <b>Инструмент содержимого читается по РАДИУСУ.</b> Счётный сигнал
     * приходит из контекста сделки, у которой инструмент есть, — и
     * положенный оттуда инструмент объявил бы радиусом пару, тогда как
     * ступень поднята на всём счёте.
     */
    @Test
    void anAccountScopeFactCarriesNoInstrumentEvenFromADealContext() {
        accountRungStands();

        holdService.raise(HoldSignal.exchangeAccount(CODE), context());

        assertThat(raisedInstruments().getFirst())
                .as("радиус — весь счёт: инструмент здесь не операнд")
                .isNull();
    }

    /**
     * <b>Эскалация пишет СВОЙ факт.</b> Она переставляет счётную ступень
     * своим машинным кодом, и без собственного события журнал и периметр
     * узнали бы только о поднятой инструментной ступени — то есть о более
     * узком радиусе, чем остановленный
     * (docs/components/SafetyHoldCoordinator.md §«Гейт терминала отчёта»).
     */
    @Test
    void theEscalatedAccountRungWritesItsOwnFact() {
        when(killSwitchService.fireInstrument(any())).thenReturn(false);
        accountRungStands();

        holdService.raise(HoldSignal.instrument(CODE), context());

        HoldSignal escalated = raisedSignals().get(1);
        assertThat(escalated.getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(escalated.getCode()).isEqualTo(Constants.Hold.EXCHANGE_KILL_SWITCH_RESIDUAL);
        assertThat(raisedInstruments().get(1)).isNull();
    }

    @Test
    void unconfirmedInstrumentTeardownEscalatesToTheAccountRadius() {
        when(killSwitchService.fireInstrument(any())).thenReturn(false);
        accountRungStands();

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(killSwitchService).fireExchangeAccount(ACCOUNT_ID);
        verify(reports).open(any(), signalWithCode(Constants.Hold.EXCHANGE_KILL_SWITCH_RESIDUAL));
    }

    @Test
    void unconfirmedAccountTeardownKeepsTheReportOpenAndDoesNotEscalateFurther() {
        when(killSwitchService.fireExchangeAccount(anyLong())).thenReturn(false);
        accountRungStands();

        holdService.raise(HoldSignal.exchangeAccount(CODE), context());

        verify(reports, never()).complete(any(), any());
        verify(killSwitchService, times(1)).fireExchangeAccount(ACCOUNT_ID);
        verify(statusEdges).enforceHardRung(any(), eq(Deal.ShutdownReason.EXCHANGE_HOLD));
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
        verify(statusEdges).enforceHardRung(any(), eq(Deal.ShutdownReason.RISK_POLICY));
    }

    @Test
    void anEmptySignalIsNotASilentTeardownRequest() {
        holdService.raise(null, context());

        verify(pairStates, never()).raiseRung(anyLong(), anyLong(), any());
        verify(accounts, never()).raiseRung(anyLong(), any());
        verify(killSwitchService, never()).fireInstrument(any());
    }

    /**
     * <b>Каскад идёт ребром энфорсмента, а не записью статуса.</b> Ребро
     * несёт причину по радиусу и факт остановки одной транзакцией; статус
     * без причины закрыл бы уводимой сделке тропу к обоим навсегда — гард
     * ребра требует активного статуса
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
     */
    @Test
    void theHardRungCascadeMovesEveryDealOfTheRadiusByTheEnforcementEdge() {
        when(deals.findCascadeSourceOnAccount(ACCOUNT_ID)).thenReturn(List.of(deal(11L), deal(12L)));
        accountRungStands();

        holdService.raise(HoldSignal.exchangeAccount(CODE), context());

        verify(statusEdges).enforceHardRung(dealWithId(11L), eq(Deal.ShutdownReason.EXCHANGE_HOLD));
        verify(statusEdges).enforceHardRung(dealWithId(12L), eq(Deal.ShutdownReason.EXCHANGE_HOLD));
    }

    /**
     * Отказ ребра на одной сделке остальных радиуса не отменяет: сделки
     * независимы, а оставленное подберёт шаг энфорсмента ближайшего
     * прохода.
     */
    @Test
    void aFailedEdgeOnOneDealDoesNotStopTheCascade() {
        when(deals.findCascadeSourceOnAccount(ACCOUNT_ID)).thenReturn(List.of(deal(11L), deal(12L)));
        when(statusEdges.enforceHardRung(dealWithId(11L), any()))
                .thenThrow(new IllegalStateException("outbox is down"));
        accountRungStands();

        assertThatCode(() -> holdService.raise(HoldSignal.exchangeAccount(CODE), context()))
                .doesNotThrowAnyException();

        verify(statusEdges).enforceHardRung(dealWithId(12L), eq(Deal.ShutdownReason.EXCHANGE_HOLD));
    }

    /**
     * <b>Первый ход энфорсмента резолвит причину по СТОЯЩЕЙ ступени, а не
     * по радиусу поднятого сигнала.</b> Состояние достижимо и названо
     * домом: на счёте стои́т ступень сворачивания, держатель поднимает
     * полную ступень на инструменте, на паре есть активная сделка. Причина
     * по радиусу сигнала дала бы здесь {@code RISK_POLICY} — то есть
     * соседние сделки одного радиуса получали бы разные durable-ответы
     * «почему», смотря чем их увели
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
     */
    @Test
    void theFirstEnforcementMoveReadsTheStandingRungAndNotTheSignalRadius() {
        accountRungStands();

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(statusEdges).enforceHardRung(dealWithId(77L), eq(Deal.ShutdownReason.EXCHANGE_HOLD));
    }

    /**
     * <b>Ступень, снятая за время реакции, каскад не обезоруживает.</b>
     * Снятие живого риска идёт шагом 3, каскад — шагом 5, поэтому сделка на
     * этой ветви стои́т активной с УЖЕ погашенным риском: пропустить её
     * значило бы оставить её шагу прохода, который её не подберёт (ступени
     * нет), и расхождению экспозиции, поднимающему ступень на весь счёт.
     * Резерв — радиус собственной реакции
     * (docs/lifecycles/Deal.md §«Причина выхода из штатного ведения»).
     */
    @Test
    void aRungClearedDuringTheReactionStillMovesTheDealByTheReactionRadius() {
        when(deals.findIdsUnderInstrumentRung(any())).thenReturn(List.of());

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(statusEdges).enforceHardRung(dealWithId(77L), eq(Deal.ShutdownReason.RISK_POLICY));
    }

    /**
     * <b>Стоящая ступень старше резерва.</b> Инструментная реакция на
     * сделке, под которой стои́т счётная ступень, пишет биржевую причину —
     * то есть резерв доходит только до сделки без единой стоящей ступени.
     */
    @Test
    void theStandingRungOutranksTheReactionRadiusFallback() {
        when(deals.findIdsUnderInstrumentRung(any())).thenReturn(List.of());
        accountRungStands();

        holdService.raise(HoldSignal.instrument(CODE), context());

        verify(statusEdges).enforceHardRung(dealWithId(77L), eq(Deal.ShutdownReason.EXCHANGE_HOLD));
    }

    /** Сделка названной идентичности. */
    private static Deal dealWithId(Long dealId) {
        return argThat(deal -> Objects.equals(dealId, deal.getId()));
    }

    /** Активная сделка популяции каскада. */
    private static Deal deal(Long dealId) {
        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setStatus(Deal.Status.ACTIVE);
        return deal;
    }

    /**
     * Сигналы всех написанных фактов подъёма, по порядку записи.
     *
     * <p><b>Читается аргумент границы, а не содержимое.</b> Форму сообщения
     * собирает шина (.claude/rules/codestyle.md §«Слой сообщения: внутренняя
     * шина»), и через мок писателя она не проходит; что перечень и код
     * доезжают до содержимого именами, мерит проба формы у писателя
     * ({@code CoreEventFormTest}).
     */
    private List<HoldSignal> raisedSignals() {
        ArgumentCaptor<HoldSignal> signals = ArgumentCaptor.forClass(HoldSignal.class);
        verify(coreEventWriter, atLeastOnce()).holdRaised(any(), signals.capture(), any(),
                any(), any());
        return signals.getAllValues();
    }

    /** Инструмент радиуса у всех написанных фактов подъёма, по порядку записи. */
    private List<String> raisedInstruments() {
        ArgumentCaptor<String> instruments = ArgumentCaptor.forClass(String.class);
        verify(coreEventWriter, atLeastOnce()).holdRaised(any(), any(), any(),
                instruments.capture(), any());
        return instruments.getAllValues();
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
        instrument.setInternalId("in-test-0001");
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
