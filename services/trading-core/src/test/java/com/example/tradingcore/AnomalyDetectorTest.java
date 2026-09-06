package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.config.AnomalyReportProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.AccountingDetectors;
import com.example.tradingcore.domain.safety.AnomalyFinding;
import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.domain.safety.AnomalyReaction;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.AnomalyScan;
import com.example.tradingcore.domain.safety.ExchangeSideDetectors;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.AnomalyReportDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Детекторы прохода и гистерезис реакции.
 *
 * <p><b>Что здесь проверяется по существу.</b> Признак чужой заявки,
 * снятый с первого тика, сносит счёт по рынку за наш собственный штатный
 * выход: закрывающая заявка уходит эндпоинтом без клиентского
 * идентификатора и висит в срезе БЕЗ маркера. Дискриминатор «строки в БД
 * нет» делает то же самое с заявкой, пережившей рестарт. Журнальная
 * находка, поднявшая ступень, заводит запрет входов там, где реакции нет
 * вовсе. Детектор непроэнфорсенной блокировки, читающий мягкую ступень,
 * рапортует о живых сущностях, которых мягкая и не обещала убирать.
 */
class AnomalyDetectorTest {

    private static final Long ACCOUNT_ID = 2L;
    private static final Long INSTRUMENT_ID = 3L;
    private static final String EXTERNAL_INSTRUMENT_ID = "ETH-USDT-SWAP";
    private static final String OUR_CLIENT_ID = "vtbabcdefghijklmnopqrstuvwxyz0123";
    private static final String FOREIGN_CLIENT_ID = "someone-else-1";

    private final AnomalyReaction reaction = mock(AnomalyReaction.class);
    private final AnomalyReportDataService reportDataService = mock(AnomalyReportDataService.class);
    private final AnomalyReportService reportService = mock(AnomalyReportService.class);
    private final HoldService holdService = mock(HoldService.class);
    private final OrderDataService orderDataService = mock(OrderDataService.class);
    private final AlgoOrderDataService algoOrderDataService = mock(AlgoOrderDataService.class);

    private final ExchangeAccount account = account();
    private final Instrument instrument = instrument();

    // --- биржевые признаки -------------------------------------------------

    /**
     * Живая сущность по инструменту вне контура — жёсткая счётная ступень
     * с ПЕРВОГО наблюдения: наш ход этого признака не производит, строка
     * инструмента нашим ходом не исчезает.
     */
    @Test
    void liveRiskOutsideTheContourReactsOnFirstSight() {
        AnomalyScan scan = scan(Map.of("BTC-USDT-SWAP", List.of(position(new BigDecimal("5")))),
                Map.of(), Map.of());

        exchangeSideDetectors().detect(scan, account, Set.of(EXTERNAL_INSTRUMENT_ID));

        AnomalyFinding finding = captured();
        assertThat(finding.getCode()).isEqualTo(Constants.Hold.EXCHANGE_FOREIGN_INSTRUMENT_RISK);
        assertThat(finding.getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(finding.getRung()).isEqualTo(HoldRung.HARD);
        assertThat(finding.getHysteresisTicks()).isEqualTo(1);
    }

    /** Больше одной позиции на инструмент — тоже с первого наблюдения. */
    @Test
    void aDuplicatePositionReactsOnFirstSight() {
        AnomalyScan scan = scan(Map.of(EXTERNAL_INSTRUMENT_ID,
                List.of(position(new BigDecimal("5")), position(new BigDecimal("7")))), Map.of(), Map.of());

        exchangeSideDetectors().detect(scan, account, Set.of(EXTERNAL_INSTRUMENT_ID));

        assertThat(captured().getCode()).isEqualTo(Constants.Hold.EXCHANGE_POSITION_MODE_VIOLATION);
    }

    /**
     * Заявка БЕЗ нашего маркера — находка, но с гистерезисом в два тика:
     * наша рыночная закрывающая уходит эндпоинтом без клиентского
     * идентификатора и висит в срезе без маркера.
     */
    @Test
    void aForeignOrderIsConfirmedByTheNextTick() {
        AnomalyScan scan = scan(Map.of(), Map.of(EXTERNAL_INSTRUMENT_ID,
                List.of(order(FOREIGN_CLIENT_ID))), Map.of());

        exchangeSideDetectors().detect(scan, account, Set.of(EXTERNAL_INSTRUMENT_ID));

        AnomalyFinding finding = captured();
        assertThat(finding.getCode()).isEqualTo(Constants.Hold.EXCHANGE_FOREIGN_ORDER);
        assertThat(finding.getHysteresisTicks()).isEqualTo(2);
    }

    /** Наша собственная заявка чужой не объявляется: маркер узнаётся. */
    @Test
    void ourOwnOrderIsNotForeign() {
        AnomalyScan scan = scan(Map.of(), Map.of(EXTERNAL_INSTRUMENT_ID,
                List.of(order(OUR_CLIENT_ID))), Map.of());

        exchangeSideDetectors().detect(scan, account, Set.of(EXTERNAL_INSTRUMENT_ID));

        verify(reaction, never()).apply(any(), any());
    }

    // --- сверка наших строк с биржей ---------------------------------------

    /**
     * Жёсткая ступень пары стои́т, а сущности на бирже живы — журнальная
     * находка: ступень уже стои́т, запрашивать её нечего, а kill-switch
     * этой реакцией не гоняется.
     */
    @Test
    void anUnenforcedHardRungOnlyJournals() {
        AnomalyScan scan = scan(Map.of(EXTERNAL_INSTRUMENT_ID, List.of(position(new BigDecimal("5")))),
                Map.of(), Map.of());

        accountingDetectors().detect(scan, account, instrument, false, Set.of(INSTRUMENT_ID), true);

        AnomalyFinding finding = capturedWithCode(Constants.Hold.SAFETY_RUNG_NOT_ENFORCED);
        assertThat(finding.getJournalOnly()).isTrue();
        assertThat(finding.getScope()).isEqualTo(HoldScope.INSTRUMENT);
    }

    /**
     * Мягкая ступень пары этого детектора не производит: она живых
     * сущностей и не обещала убирать, а детектор наблюдает неисполненное
     * обещание жёсткой.
     */
    @Test
    void aSoftRungDoesNotProduceTheUnenforcedFinding() {
        AnomalyScan scan = scan(Map.of(EXTERNAL_INSTRUMENT_ID, List.of(position(new BigDecimal("5")))),
                Map.of(), Map.of());

        accountingDetectors().detect(scan, account, instrument, false, Set.of(), true);

        verify(reaction, never()).apply(any(), any());
    }

    /**
     * Локально терминальная сущность, живая на бирже, — журнальная находка
     * с ПРЕДМЕТОМ: без предмета в ключе два разных расхождения по одному
     * инструменту схлопнулись бы в одну строку.
     */
    @Test
    void aLocallyTerminalEntityAliveOnExchangeCarriesItsSubject() {
        when(orderDataService.findByInternalId(OUR_CLIENT_ID))
                .thenReturn(Optional.of(terminalOrder()));
        AnomalyScan scan = scan(Map.of(EXTERNAL_INSTRUMENT_ID, List.of(position(new BigDecimal("5")))),
                Map.of(EXTERNAL_INSTRUMENT_ID, List.of(order(OUR_CLIENT_ID))), Map.of());

        accountingDetectors().detect(scan, account, instrument, false, Set.of(), true);

        AnomalyFinding finding = capturedWithCode(Constants.Hold.LOCAL_TERMINAL_ALIVE_ON_EXCHANGE);
        assertThat(finding.getSubjectExternalId()).isEqualTo(OUR_CLIENT_ID);
        assertThat(finding.getJournalOnly()).isTrue();
    }

    /**
     * Хвосты заявок без позиции и без живой сделки — мягкая инструментная
     * ступень: живого направленного риска у хвоста нет, снимать нечего.
     */
    @Test
    void orphanOrdersRaiseTheSoftInstrumentRung() {
        AnomalyScan scan = scan(Map.of(), Map.of(EXTERNAL_INSTRUMENT_ID,
                List.of(order(OUR_CLIENT_ID))), Map.of());
        when(orderDataService.findByInternalId(OUR_CLIENT_ID)).thenReturn(Optional.of(liveOrder()));

        accountingDetectors().detect(scan, account, instrument, false, Set.of(), false);

        AnomalyFinding finding = capturedWithCode(Constants.Hold.INSTRUMENT_ORPHAN_ORDERS);
        assertThat(finding.getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(finding.getRung()).isEqualTo(HoldRung.SOFT);
    }

    /**
     * Живая сделка хвост объясняет: наша штатная отдыхающая входная заявка
     * позиции ещё не имеет по построению, и без операнда БД детектор
     * срабатывал бы на каждом нормальном входе.
     */
    @Test
    void anExplainedOrderIsNotAnOrphan() {
        AnomalyScan scan = scan(Map.of(), Map.of(EXTERNAL_INSTRUMENT_ID,
                List.of(order(OUR_CLIENT_ID))), Map.of());
        when(orderDataService.findByInternalId(OUR_CLIENT_ID)).thenReturn(Optional.of(liveOrder()));

        accountingDetectors().detect(scan, account, instrument, false, Set.of(), true);

        verify(reaction, never()).apply(any(), any());
    }

    // --- гистерезис реакции ------------------------------------------------

    /** Находка без гистерезиса поднимает ступень сразу. */
    @Test
    void aFindingWithoutHysteresisRaisesTheRungAtOnce() {
        anomalyReaction().apply(finding(1, false, HoldScope.EXCHANGE_ACCOUNT, HoldRung.HARD), account);

        verify(holdService).raise(eq(new HoldSignal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.HARD, "CODE")),
                any(DealContext.class));
    }

    /**
     * Находка с гистерезисом на первом тике заводит наблюдательную строку
     * и ступени не поднимает: durable-факт «признак наблюдался» и есть
     * стоящая строка, переживающая рестарт.
     */
    @Test
    void aFindingWithHysteresisJournalsFirstAndWaits() {
        anomalyReaction().apply(finding(2, false, HoldScope.INSTRUMENT, HoldRung.HARD), account);

        verify(reportService).journalState(any(), eq(HoldSignal.instrumentJournal("CODE")), eq(null));
        verify(holdService, never()).raise(any(), any());
    }

    /** Подтверждённый стоящей строкой признак поднимает свою ступень. */
    @Test
    void aConfirmedFindingRaisesItsRung() {
        when(reportDataService.existsStanding(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), eq(null), eq("CODE"),
                eq(AnomalyReport.Severity.NON_CRITICAL), any(OffsetDateTime.class),
                any(OffsetDateTime.class))).thenReturn(Boolean.TRUE);

        anomalyReaction().apply(finding(2, false, HoldScope.INSTRUMENT, HoldRung.HARD), account);

        verify(holdService).raise(eq(new HoldSignal(HoldScope.INSTRUMENT, HoldRung.HARD, "CODE")),
                any(DealContext.class));
    }

    /**
     * Журнальная находка ступени не поднимает даже подтверждённой: у неё
     * блокировки нет в составе реакции, а стоящая строка служит ей
     * дедупом, а не операндом ступени.
     */
    @Test
    void aJournalOnlyFindingNeverRaisesARung() {
        when(reportDataService.existsStanding(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Boolean.TRUE);

        anomalyReaction().apply(finding(2, true, HoldScope.INSTRUMENT, HoldRung.SOFT), account);

        verify(holdService, never()).raise(any(), any());
    }

    // --- сборка ------------------------------------------------------------

    private ExchangeSideDetectors exchangeSideDetectors() {
        return new ExchangeSideDetectors(reaction);
    }

    private AccountingDetectors accountingDetectors() {
        return new AccountingDetectors(orderDataService, algoOrderDataService, reaction);
    }

    private AnomalyReaction anomalyReaction() {
        return new AnomalyReaction(reportDataService, reportService, holdService,
                new AnomalyJobProperties(), new AnomalyReportProperties());
    }

    private AnomalyFinding captured() {
        ArgumentCaptor<AnomalyFinding> captor = ArgumentCaptor.forClass(AnomalyFinding.class);
        verify(reaction).apply(captor.capture(), eq(account));
        return captor.getValue();
    }

    private AnomalyFinding capturedWithCode(String code) {
        ArgumentCaptor<AnomalyFinding> captor = ArgumentCaptor.forClass(AnomalyFinding.class);
        verify(reaction, atLeastOnce()).apply(captor.capture(), eq(account));
        return captor.getAllValues().stream()
                .filter(finding -> Objects.equals(code, finding.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding with code " + code));
    }

    private AnomalyFinding finding(Integer hysteresis, Boolean journalOnly, HoldScope scope, HoldRung rung) {
        return AnomalyFinding.builder()
                .scope(scope)
                .rung(rung)
                .code("CODE")
                .instrument(instrument)
                .hysteresisTicks(hysteresis)
                .journalOnly(journalOnly)
                .build();
    }

    private AnomalyScan scan(Map<String, List<Position>> positions, Map<String, List<Order>> orders,
                             Map<String, List<AlgoOrder>> algoOrders) {
        return AnomalyScan.builder()
                .positions(positions)
                .orders(orders)
                .algoOrders(algoOrders)
                .complete(true)
                .build();
    }

    private static ExchangeAccount account() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId("ea-0001");
        account.setExchangeCode("OKX");
        account.setSafetyRung(ExchangeAccount.SafetyRung.ACTIVE);
        return account;
    }

    private static Instrument instrument() {
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        instrument.setExternalId(EXTERNAL_INSTRUMENT_ID);
        return instrument;
    }

    private Position position(BigDecimal size) {
        Position position = new Position();
        position.setExternalSize(size);
        position.setDirection(Position.Direction.LONG);
        return position;
    }

    private Order order(String internalId) {
        Order order = new Order();
        order.setInternalId(internalId);
        order.setExternalInstrumentId(EXTERNAL_INSTRUMENT_ID);
        return order;
    }

    /** Наша строка терминальна: сущность на бирже жива, а у нас закрыта. */
    private Order terminalOrder() {
        Order order = new Order();
        order.setInternalId(OUR_CLIENT_ID);
        order.setStatus(Order.Status.CANCELED);
        return order;
    }

    /** Наша строка жива: расхождения нет. */
    private Order liveOrder() {
        Order order = new Order();
        order.setInternalId(OUR_CLIENT_ID);
        order.setStatus(Order.Status.ACTIVE);
        return order;
    }
}
