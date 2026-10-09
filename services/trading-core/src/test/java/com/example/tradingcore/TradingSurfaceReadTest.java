package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.api.controller.TradingSurfaceController;
import com.example.tradingcore.api.model.AccountInstrumentStateApiRequest;
import com.example.tradingcore.api.model.DealApiResponse;
import com.example.tradingcore.api.model.DealTrancheApiResponse;
import com.example.tradingcore.api.model.RiskAppetiteApiResponse;
import com.example.tradingcore.api.model.SafetyStateApiResponse;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.domain.model.RiskAppetite;
import com.example.tradingcore.domain.service.RiskAppetiteService;
import com.example.tradingcore.domain.service.TradingSurfaceService;
import com.example.tradingcore.mapping.TradingSurfaceMapperImpl;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.BalanceContainerDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealCashFlowDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.persistence.service.PositionDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Поверхность чтения торгового состояния и принятых чисел риск-аппетита,
 * назначение плеча пары.
 *
 * <p><b>Что здесь проверяется по существу.</b> Наружу поверхность обязана
 * отдавать {@code internalId}, а не ключ БД: числовой ключ границу
 * сервиса не пересекает, и утёкший ключ становится частью контракта, из
 * которого его уже не убрать. Резолв этих идентичностей обязан идти
 * ОДНОЙ раскладкой на выборку: чтение на строку окна — запрос в цикле
 * длиной в окно, и растёт он вместе с историей счёта, а не с нагрузкой.
 * Принятые числа риск-аппетита обязаны ехать наружу маппером все шесть, а
 * назначение плеча пары — сверяться с принятым пределом плеча.
 *
 * <p>Маппер — НАСТОЯЩИЙ (сгенерированный MapStruct), не подменённый:
 * предмет половины проверок — именно перенос полей. Сборка графа сделки —
 * тоже настоящая: экспозиция транша есть производная его ног и приписанного
 * закрытия уровня сделки, и подменённая сборка проверяла бы поверхность
 * против вывода, которого в проде нет.
 */
class TradingSurfaceReadTest {

    private static final Long ACCOUNT_ID = 2L;
    private static final Long FIRST_INSTRUMENT_ID = 3L;
    private static final Long SECOND_INSTRUMENT_ID = 4L;
    private static final String ACCOUNT_INTERNAL_ID = "ea-0001";
    private static final String FIRST_INSTRUMENT_INTERNAL_ID = "in-0001";
    private static final String SECOND_INSTRUMENT_INTERNAL_ID = "in-0002";

    private final DealDataService deals = mock(DealDataService.class);
    private final DealTrancheDataService tranches = mock(DealTrancheDataService.class);
    private final OrderDataService orders = mock(OrderDataService.class);
    private final AlgoOrderDataService algoOrders = mock(AlgoOrderDataService.class);
    private final PositionDataService positions = mock(PositionDataService.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final InstrumentDataService instruments = mock(InstrumentDataService.class);
    private final AccountInstrumentStateDataService pairStates =
            mock(AccountInstrumentStateDataService.class);
    private final RiskAppetiteService appetites = mock(RiskAppetiteService.class);

    private final DealContextService dealContext = new DealContextService(accounts, instruments,
            mock(StrategyDataService.class), orders, algoOrders, positions, tranches,
            mock(BalanceContainerDataService.class), mock(DealActionStateDataService.class),
            mock(DealCashFlowDataService.class), mock(MarketFeatureService.class), new DealContextProperties());

    private final TradingSurfaceService service = new TradingSurfaceService(deals, dealContext, accounts,
            instruments, pairStates, appetites);
    private final TradingSurfaceController controller =
            new TradingSurfaceController(service, new TradingSurfaceMapperImpl());

    /**
     * Ответ несёт идентичности связанных сущностей, а ключей БД не несёт
     * ни одного.
     */
    @Test
    void theDealResponseCarriesIdentitiesAndNoDatabaseKeys() {
        givenAccount();
        when(deals.findRecentOnAccount(anyLong(), anyInt()))
                .thenReturn(List.of(deal(FIRST_INSTRUMENT_ID)));
        when(instruments.findInternalIdsByIds(anyCollection()))
                .thenReturn(Map.of(FIRST_INSTRUMENT_ID, FIRST_INSTRUMENT_INTERNAL_ID));

        List<DealApiResponse> responses = controller.findDeals(ACCOUNT_INTERNAL_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getExchangeAccountInternalId()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(responses.getFirst().getInstrumentInternalId())
                .isEqualTo(FIRST_INSTRUMENT_INTERNAL_ID);
    }

    /**
     * Идентичности инструментов всей выборки резолвятся ОДНИМ чтением, а
     * не по одному на строку.
     *
     * <p>Именно это и есть предмет: чтение на строку растёт вместе с
     * окном истории счёта, а окно — сотня строк, и на каждый показ
     * поверхности это сотня запросов при одном достаточном.
     */
    @Test
    void identitiesOfTheWholeWindowAreResolvedByASingleRead() {
        givenAccount();
        when(deals.findRecentOnAccount(anyLong(), anyInt()))
                .thenReturn(List.of(deal(FIRST_INSTRUMENT_ID), deal(SECOND_INSTRUMENT_ID),
                        deal(FIRST_INSTRUMENT_ID)));
        when(instruments.findInternalIdsByIds(anyCollection())).thenReturn(Map.of(
                FIRST_INSTRUMENT_ID, FIRST_INSTRUMENT_INTERNAL_ID,
                SECOND_INSTRUMENT_ID, SECOND_INSTRUMENT_INTERNAL_ID));

        List<DealApiResponse> responses = controller.findDeals(ACCOUNT_INTERNAL_ID);

        assertThat(responses).hasSize(3);
        verify(instruments, times(1)).findInternalIdsByIds(anyCollection());
        verify(instruments, never()).getRequiredById(anyLong());
    }

    /**
     * Сделка, чьи транши погасило одно закрытие уровня сделки: экспозиция
     * каждого закрытого транша отдаётся с приписанным ему объёмом — нулём, а
     * не налитым входом. Строка транша несёт налив, записанный последним
     * проходом, и не несёт приписанного: чтение строк без сборки графа
     * отдавало бы налитое.
     */
    @Test
    void aTrancheClosedByTheDealLevelCloseIsReadWithItsAttributedVolume() {
        Deal deal = deal(FIRST_INSTRUMENT_ID);
        deal.setStatus(Deal.Status.CLOSED);
        ExchangeAccount account = givenAccount();
        when(accounts.getRequiredById(ACCOUNT_ID)).thenReturn(account);
        when(deals.getRequiredByInternalId(deal.getInternalId())).thenReturn(deal);
        when(tranches.findByDealId(deal.getId()))
                .thenReturn(new ArrayList<>(List.of(closedTranche(10L, "5"), closedTranche(11L, "3"))));
        when(orders.findByDealId(deal.getId())).thenReturn(new ArrayList<>(List.of(
                filledEntry(100L, 10L, "5"), filledEntry(110L, 11L, "3"))));
        when(algoOrders.findByDealId(deal.getId())).thenReturn(new ArrayList<>());
        when(positions.findEpisodes(deal.getId())).thenReturn(new ArrayList<>(List.of(closedEpisode())));
        when(instruments.findInternalIdsByIds(anyCollection()))
                .thenReturn(Map.of(FIRST_INSTRUMENT_ID, FIRST_INSTRUMENT_INTERNAL_ID));

        DealApiResponse response = controller.getDeal(deal.getInternalId());

        assertThat(response.getTranches()).hasSize(2)
                .extracting(DealTrancheApiResponse::getExposure)
                .allSatisfy(exposure -> assertThat(exposure).isEqualByComparingTo(BigDecimal.ZERO));
    }

    /**
     * Торговое состояние счёта: ступень и статус едут строками, ступени
     * стоящих пар — идентичностью инструмента и именем ступени, а не одним
     * фактом стояния: снятие обязано назвать ступень.
     */
    @Test
    void theSafetyStateCarriesTheRungAndTheStandingPairs() {
        givenAccount();
        when(pairStates.findWithStandingRung(ACCOUNT_ID))
                .thenReturn(List.of(pairState(SECOND_INSTRUMENT_ID, Instrument.SafetyRung.ENTRY_BLOCKED)));
        when(instruments.findInternalIdsByIds(anyCollection()))
                .thenReturn(Map.of(SECOND_INSTRUMENT_ID, SECOND_INSTRUMENT_INTERNAL_ID));

        SafetyStateApiResponse response = controller.getSafetyState(ACCOUNT_INTERNAL_ID);

        assertThat(response.getExchangeAccountInternalId()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(response.getAccountSafetyRung())
                .isEqualTo(ExchangeAccount.SafetyRung.HOLD.name());
        assertThat(response.getAccountStatus()).isEqualTo(ExchangeAccount.Status.ACTIVE.name());
        assertThat(response.getStandingInstrumentRungs())
                .containsExactly(Map.entry(SECOND_INSTRUMENT_INTERNAL_ID,
                        Instrument.SafetyRung.ENTRY_BLOCKED.name()));
    }

    /** Строка пары в названной ступени. */
    private static AccountInstrumentState pairState(Long instrumentId, Instrument.SafetyRung rung) {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setExchangeAccountId(ACCOUNT_ID);
        state.setInstrumentId(instrumentId);
        state.setSafetyRung(rung);
        return state;
    }

    /** Чтение принятых чисел: все шесть едут наружу маппером своими значениями. */
    @Test
    void theAcceptedAppetiteIsReadWithAllSixNumbers() {
        when(appetites.getAccepted()).thenReturn(RiskAppetite.builder()
                .globalSimultaneousRiskPerDealPercent(new BigDecimal("1"))
                .globalSimultaneousRiskPerAccountPercent(new BigDecimal("10"))
                .globalSimultaneousRiskPerTenantPercent(new BigDecimal("30"))
                .globalCumulativeRiskPerDealMultiplier(new BigDecimal("2"))
                .globalMaxLeverage(new BigDecimal("10"))
                .globalConsecutiveLossLimit(3)
                .build());

        RiskAppetiteApiResponse response = controller.getRiskAppetite();

        assertThat(response.getGlobalSimultaneousRiskPerDealPercent()).isEqualByComparingTo("1");
        assertThat(response.getGlobalSimultaneousRiskPerAccountPercent()).isEqualByComparingTo("10");
        assertThat(response.getGlobalSimultaneousRiskPerTenantPercent()).isEqualByComparingTo("30");
        assertThat(response.getGlobalCumulativeRiskPerDealMultiplier()).isEqualByComparingTo("2");
        assertThat(response.getGlobalMaxLeverage()).isEqualByComparingTo("10");
        assertThat(response.getGlobalConsecutiveLossLimit()).isEqualTo(3);
    }

    /** Плечо не выше предела назначается: граница включена. */
    @Test
    void aLeverageAtTheLimitIsAssigned() {
        givenAccount();
        givenMaxLeverage("10");
        when(instruments.getRequiredIdByInternalId(FIRST_INSTRUMENT_INTERNAL_ID)).thenReturn(FIRST_INSTRUMENT_ID);
        when(pairStates.assignLeverage(ACCOUNT_ID, FIRST_INSTRUMENT_ID, 10))
                .thenReturn(pairState(FIRST_INSTRUMENT_ID, Instrument.SafetyRung.ACTIVE));

        controller.applyPairSettings(ACCOUNT_INTERNAL_ID, FIRST_INSTRUMENT_INTERNAL_ID, leverageRequest(10));

        verify(pairStates).assignLeverage(ACCOUNT_ID, FIRST_INSTRUMENT_ID, 10);
    }

    /** Плечо выше предела — негодный вход вызова, строка пары не трогается. */
    @Test
    void aLeverageAboveTheLimitIsRefusedAsABadRequest() {
        givenAccount();
        givenMaxLeverage("10");

        assertThatThrownBy(() -> controller.applyPairSettings(ACCOUNT_INTERNAL_ID, FIRST_INSTRUMENT_INTERNAL_ID,
                leverageRequest(11)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(pairStates, never()).assignLeverage(any(), any(), any());
    }

    /** Снять плечо можно всегда: снятие пределом не сверяется. */
    @Test
    void aLeverageCanAlwaysBeCleared() {
        givenAccount();
        givenMaxLeverage("10");
        when(instruments.getRequiredIdByInternalId(FIRST_INSTRUMENT_INTERNAL_ID)).thenReturn(FIRST_INSTRUMENT_ID);
        when(pairStates.assignLeverage(ACCOUNT_ID, FIRST_INSTRUMENT_ID, null))
                .thenReturn(pairState(FIRST_INSTRUMENT_ID, Instrument.SafetyRung.ACTIVE));

        controller.applyPairSettings(ACCOUNT_INTERNAL_ID, FIRST_INSTRUMENT_INTERNAL_ID, leverageRequest(null));

        verify(pairStates).assignLeverage(ACCOUNT_ID, FIRST_INSTRUMENT_ID, null);
    }

    private void givenMaxLeverage(String maxLeverage) {
        when(appetites.getAccepted()).thenReturn(RiskAppetite.builder()
                .globalMaxLeverage(new BigDecimal(maxLeverage))
                .build());
    }

    private static AccountInstrumentStateApiRequest leverageRequest(Integer leverage) {
        AccountInstrumentStateApiRequest request = new AccountInstrumentStateApiRequest();
        request.setLeverage(leverage);
        return request;
    }

    private ExchangeAccount givenAccount() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setStatus(ExchangeAccount.Status.ACTIVE);
        account.setSafetyRung(ExchangeAccount.SafetyRung.HOLD);
        account.setConsecutiveLossCount(1);
        account.setBlindPassCount(0);
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account);
        return account;
    }

    /**
     * Строка закрытого транша: налив входа записан последним проходом, а
     * приписанного закрытия колонкой нет — его выводит только сборка графа.
     */
    private DealTranche closedTranche(Long id, String persistedEntryFill) {
        DealTranche tranche = new DealTranche();
        tranche.setId(id);
        tranche.setInternalId("tr-" + id);
        tranche.setStatus(DealTranche.Status.CLOSED);
        tranche.setEntryFilled(new BigDecimal(persistedEntryFill));
        return tranche;
    }

    private Order filledEntry(Long id, Long trancheId, String fill) {
        Order order = new Order();
        order.setId(id);
        order.setDealTrancheId(trancheId);
        order.setStatus(Order.Status.COMPLETED);
        order.setAccumulatedFillSize(new BigDecimal(fill));
        order.setPositionReducingOnly(Boolean.FALSE);
        return order;
    }

    /** Эпизод закрыт и несёт необнулённый размер — так его оставляет площадка. */
    private Position closedEpisode() {
        Position episode = new Position();
        episode.setStatus(Position.Status.CLOSED);
        episode.setExternalSize(new BigDecimal("8"));
        return episode;
    }

    private Deal deal(Long instrumentId) {
        Deal deal = new Deal();
        deal.setId(instrumentId);
        deal.setInternalId("dl-000" + instrumentId);
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setInstrumentId(instrumentId);
        deal.setStatus(Deal.Status.ACTIVE);
        return deal;
    }
}
