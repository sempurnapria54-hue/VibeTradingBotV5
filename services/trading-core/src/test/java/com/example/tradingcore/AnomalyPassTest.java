package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealOpeningService;
import com.example.tradingcore.domain.jobs.AnomalyJob;
import com.example.tradingcore.domain.jobs.JobExecutionGuard;
import com.example.tradingcore.domain.safety.AccountingDetectors;
import com.example.tradingcore.domain.safety.AnomalyPassGate;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.AnomalyScan;
import com.example.tradingcore.domain.safety.AnomalyScanReader;
import com.example.tradingcore.domain.safety.DealInvariantDetectors;
import com.example.tradingcore.domain.safety.ExchangeSideDetectors;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.integration.internal.api.exchange.ExternalInvariantViolationException;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Проход проактивной детекции: сбор среза счёта, гейт полноты, счёт
 * слепоты и восстановительная тропа.
 *
 * <p><b>Что здесь проверяется по существу.</b> Строка закрытой позиции,
 * не отфильтрованная в срезе, объявляет чужой закрытую позицию и молчит
 * там, где предмет детектора — «позиции нет, а заявки есть». Проход,
 * отмеченный полным ДО обхода, сбрасывает счёт слепоты на том проходе,
 * где детекция упала, — и «не смотрели» становится неотличимо от «ничего
 * не нашли». Детекторы, не смолчавшие на неполном срезе, реагируют на
 * расхождение, которое производит сама неполнота. Живая позиция без
 * сделки, оставленная без восстановительной тропы, остаётся вне модели.
 */
class AnomalyPassTest {

    private static final Long ACCOUNT_ID = 2L;
    private static final Long INSTRUMENT_ID = 3L;
    private static final String ACCOUNT_INTERNAL_ID = "ea-0001";
    private static final String EXCHANGE_CODE = "OKX";
    private static final String EXTERNAL_INSTRUMENT_ID = "ETH-USDT-SWAP";
    private static final OffsetDateTime OPENED_AT =
            OffsetDateTime.of(2026, 9, 6, 9, 0, 0, 0, ZoneOffset.UTC);

    private final ExchangeOperationsClient exchangeOperationsClient = mock(ExchangeOperationsClient.class);
    private final ExchangeAccountDataService exchangeAccountDataService =
            mock(ExchangeAccountDataService.class);
    private final AccountInstrumentStateDataService accountInstrumentStateDataService =
            mock(AccountInstrumentStateDataService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final DealDataService dealDataService = mock(DealDataService.class);
    private final AnomalyScanReader scanReader = mock(AnomalyScanReader.class);
    private final AnomalyPassGate passGate = mock(AnomalyPassGate.class);
    private final ExchangeSideDetectors exchangeSideDetectors = mock(ExchangeSideDetectors.class);
    private final AccountingDetectors accountingDetectors = mock(AccountingDetectors.class);
    private final DealInvariantDetectors dealInvariantDetectors = mock(DealInvariantDetectors.class);
    private final DealOpeningService dealOpeningService = mock(DealOpeningService.class);
    private final AnomalyReportService reportService = mock(AnomalyReportService.class);
    private final HoldService holdService = mock(HoldService.class);

    private final ExchangeAccount account = newAccount();

    // --- сбор среза --------------------------------------------------------

    /**
     * Строка закрытой позиции (нулевой размер) из среза выбывает.
     * Нормализация одна на всех потребителей: иначе один детектор объявил
     * бы чужой закрытую позицию, а другой промолчал бы ровно в своей
     * популяции.
     */
    @Test
    void aClosedPositionRowLeavesTheSlice() {
        when(exchangeOperationsClient.getPositions(ACCOUNT_INTERNAL_ID))
                .thenReturn(List.of(position(BigDecimal.ZERO), position(new BigDecimal("5"))));

        AnomalyScan scan = reader().read(ACCOUNT_INTERNAL_ID);

        assertThat(scan.positionsOf(EXTERNAL_INSTRUMENT_ID)).singleElement()
                .extracting(Position::getExternalSize).isEqualTo(new BigDecimal("5"));
    }

    /**
     * Неполученный срез делает проход неполным, а остальные срезы всё
     * равно собираются: предмет — неполнота ПРОХОДА, а не отказ вызова.
     */
    @Test
    void anUnharvestedSliceMakesThePassIncomplete() {
        when(exchangeOperationsClient.getPositions(ACCOUNT_INTERNAL_ID))
                .thenThrow(new IllegalStateException("connector is unreachable"));
        when(exchangeOperationsClient.getAllPendingOrders(ACCOUNT_INTERNAL_ID))
                .thenReturn(List.of(order("vtb-order-1")));

        AnomalyScan scan = reader().read(ACCOUNT_INTERNAL_ID);

        assertThat(scan.getComplete()).isFalse();
        assertThat(scan.ordersOf(EXTERNAL_INSTRUMENT_ID)).hasSize(1);
    }

    /**
     * Контролируемое исключение наверх проходит и пометкой «проход
     * неполон» не подменяется: оно поднимает биржевую ступень 2 само, а
     * подмена смягчила бы ратифицированный исход.
     */
    @Test
    void aControlledFailureIsNotDowngradedToAnIncompletePass() {
        when(exchangeOperationsClient.getPositions(ACCOUNT_INTERNAL_ID))
                .thenThrow(new ExternalInvariantViolationException("margin mode is not isolated"));

        assertThatThrownBy(() -> reader().read(ACCOUNT_INTERNAL_ID))
                .isInstanceOf(ExternalInvariantViolationException.class);
    }

    /** Строка среза без биржевого имени инструмента адресована быть не может. */
    @Test
    void aRowWithoutTheInstrumentNameIsNotAddressed() {
        Order homeless = order("vtb-order-2");
        homeless.setExternalInstrumentId(null);
        when(exchangeOperationsClient.getAllPendingOrders(ACCOUNT_INTERNAL_ID))
                .thenReturn(List.of(homeless));

        assertThat(reader().read(ACCOUNT_INTERNAL_ID).instrumentsWithLiveEntities()).isEmpty();
    }

    // --- гейт полноты и счёт слепоты ---------------------------------------

    /** Наблюдённый проход сбрасывает счёт слепоты и реакции не поднимает. */
    @Test
    void anObservedPassResetsTheBlindCount() {
        when(exchangeAccountDataService.markPass(ACCOUNT_ID, Boolean.TRUE)).thenReturn(0);

        gate().apply(Boolean.TRUE, account());

        verify(holdService, never()).raise(any(), any());
        verify(reportService, never()).journalState(any(), any(), any());
    }

    /**
     * Ненаблюдённый проход до предела заводит только строку журнала:
     * «ничего не нашли» и «не смотрели» обязаны быть различимы в данных.
     */
    @Test
    void aBlindPassBelowTheLimitOnlyJournals() {
        when(exchangeAccountDataService.markPass(ACCOUNT_ID, Boolean.FALSE)).thenReturn(2);

        gate().apply(Boolean.FALSE, account());

        verify(reportService).journalState(any(), eq(HoldSignal.exchangeAccountJournal(
                Constants.Hold.ANOMALY_PASS_INCOMPLETE)), eq(null));
        verify(holdService, never()).raise(any(), any());
    }

    /**
     * Достигнутый предел слепоты поднимает МЯГКУЮ счётную ступень:
     * биржевая защита продолжает стоять, пока мы её не видим, и жёсткая
     * снимала бы покрытый риск по рынку из-за отказа канала.
     */
    @Test
    void theBlindLimitRaisesTheSoftAccountRung() {
        when(exchangeAccountDataService.markPass(ACCOUNT_ID, Boolean.FALSE)).thenReturn(3);

        gate().apply(Boolean.FALSE, account());

        verify(holdService).raise(eq(new HoldSignal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.SOFT,
                Constants.Hold.ANOMALY_PASS_INCOMPLETE)), any(DealContext.class));
    }

    // --- проход ------------------------------------------------------------

    /** На неполном срезе детекторы молчат, а проход отмечается ненаблюдённым. */
    @Test
    void detectorsStaySilentOnAnIncompletePass() {
        givenAccount();
        when(scanReader.read(ACCOUNT_INTERNAL_ID)).thenReturn(scan(false));

        job().tick();

        verify(exchangeSideDetectors, never()).detect(any(), any(), any());
        verify(dealInvariantDetectors, never()).detect(any());
        verify(passGate).apply(Boolean.FALSE, account());
    }

    /**
     * Упор выборки контура в окно засчитывается неполнотой прохода: обход
     * по усечённому контуру объявил бы чужими строки среза, которым не
     * хватило места в выборке.
     */
    @Test
    void aContourHittingTheWindowCountsAsIncomplete() {
        givenAccount();
        when(scanReader.read(ACCOUNT_INTERNAL_ID)).thenReturn(scan(true));
        AnomalyJobProperties narrow = properties();
        narrow.setContourWindow(1);
        when(instrumentDataService.findContourWithin(EXCHANGE_CODE, 1))
                .thenReturn(new ArrayList<>(List.of(instrument())));

        job(narrow).tick();

        verify(exchangeSideDetectors, never()).detect(any(), any(), any());
        verify(passGate).apply(Boolean.FALSE, account());
    }

    /**
     * Проход отмечается ПОСЛЕ детекции, и упавшая детекция даёт
     * ненаблюдённый проход: отметка «полон» до обхода сбрасывала бы счёт
     * слепоты ровно там, где никто не смотрел.
     */
    @Test
    void aFailedDetectionMarksThePassAsBlind() {
        givenAccount();
        when(scanReader.read(ACCOUNT_INTERNAL_ID)).thenReturn(scan(true));
        givenContour();
        doThrow(new IllegalStateException("detection is broken"))
                .when(exchangeSideDetectors).detect(any(), any(), any());

        job().tick();

        InOrder order = inOrder(exchangeSideDetectors, passGate);
        order.verify(exchangeSideDetectors).detect(any(), any(), any());
        order.verify(passGate).apply(Boolean.FALSE, account());
    }

    /**
     * Живая позиция, не объяснимая ни одной сделкой, заводит сделку
     * восстановительной тропой тем же тиком: без этого вызова найденный
     * риск остаётся вне модели.
     */
    @Test
    void anUnexplainedLivePositionRecoversItsDeal() {
        givenCompletePass();

        job().tick();

        verify(dealOpeningService).recoverDeal(eq(account()), any(), eq(StrategyTradeDirection.LONG),
                eq(OPENED_AT));
    }

    /** Позицию, которую объясняет живая сделка, восстанавливать не надо. */
    @Test
    void anExplainedPositionIsNotRecovered() {
        givenCompletePass();
        when(dealDataService.findInstrumentIdsWithActiveDeal(ACCOUNT_ID))
                .thenReturn(Set.of(INSTRUMENT_ID));

        job().tick();

        verify(dealOpeningService, never()).recoverDeal(any(), any(), any(), any());
    }

    /**
     * Знак размера не определён — сторону подставлять нечем, и сделка
     * вокруг наблюдённого факта не заводится.
     */
    @Test
    void aPositionWithoutADirectionIsNotRecovered() {
        Position undetermined = position(new BigDecimal("5"));
        undetermined.setDirection(null);
        givenCompletePass(undetermined);

        job().tick();

        verify(dealOpeningService, never()).recoverDeal(any(), any(), any(), any());
    }

    /** Выключенный проход не делает ничего — ни выборки счетов, ни среза. */
    @Test
    void aDisabledPassDoesNothing() {
        AnomalyJobProperties disabled = properties();
        disabled.setEnabled(Boolean.FALSE);

        job(disabled).tick();

        verify(exchangeAccountDataService, never()).findTradingAccounts();
    }

    // --- сборка ------------------------------------------------------------

    private AnomalyScanReader reader() {
        return new AnomalyScanReader(exchangeOperationsClient);
    }

    private AnomalyPassGate gate() {
        return new AnomalyPassGate(exchangeAccountDataService, reportService, holdService, properties());
    }

    private AnomalyJob job() {
        return job(properties());
    }

    private AnomalyJob job(AnomalyJobProperties properties) {
        return new AnomalyJob(properties, new JobExecutionGuard(), exchangeAccountDataService,
                accountInstrumentStateDataService, instrumentDataService, dealDataService, scanReader,
                passGate, exchangeSideDetectors, accountingDetectors, dealInvariantDetectors,
                dealOpeningService);
    }

    private AnomalyJobProperties properties() {
        return new AnomalyJobProperties();
    }

    private void givenAccount() {
        when(exchangeAccountDataService.findTradingAccounts())
                .thenReturn(new ArrayList<>(List.of(account())));
    }

    private void givenContour() {
        when(instrumentDataService.findContourWithin(eq(EXCHANGE_CODE), any()))
                .thenReturn(new ArrayList<>(List.of(instrument())));
    }

    /** Полный проход с одной живой позицией по инструменту контура. */
    private void givenCompletePass() {
        givenCompletePass(position(new BigDecimal("5")));
    }

    private void givenCompletePass(Position position) {
        givenAccount();
        givenContour();
        when(scanReader.read(ACCOUNT_INTERNAL_ID)).thenReturn(AnomalyScan.builder()
                .positions(Map.of(EXTERNAL_INSTRUMENT_ID, List.of(position)))
                .orders(Map.of())
                .algoOrders(Map.of())
                .complete(true)
                .build());
    }

    private AnomalyScan scan(boolean complete) {
        return AnomalyScan.builder()
                .positions(Map.of())
                .orders(Map.of())
                .algoOrders(Map.of())
                .complete(complete)
                .build();
    }

    /**
     * Счёт прохода — ОДИН экземпляр на тест: у доменной модели равенства
     * по значению нет, и второй экземпляр не совпал бы с тем, который
     * проход передал дальше.
     */
    private ExchangeAccount account() {
        return account;
    }

    private static ExchangeAccount newAccount() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setExchangeCode(EXCHANGE_CODE);
        account.setSafetyRung(ExchangeAccount.SafetyRung.ACTIVE);
        return account;
    }

    private Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId(EXTERNAL_INSTRUMENT_ID);
        instrument.setExchangeCode(EXCHANGE_CODE);
        return instrument;
    }

    private Position position(BigDecimal size) {
        Position position = new Position();
        position.setExternalId("pos-1");
        position.setExternalInstrumentId(EXTERNAL_INSTRUMENT_ID);
        position.setExternalSize(size);
        position.setDirection(Position.Direction.LONG);
        position.setExternalCreatedAt(OPENED_AT);
        return position;
    }

    private Order order(String internalId) {
        Order order = new Order();
        order.setInternalId(internalId);
        order.setExternalInstrumentId(EXTERNAL_INSTRUMENT_ID);
        return order;
    }
}
