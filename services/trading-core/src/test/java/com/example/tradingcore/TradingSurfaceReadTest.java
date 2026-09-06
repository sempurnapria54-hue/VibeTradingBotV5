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
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.api.controller.TradingSurfaceController;
import com.example.tradingcore.api.model.DealApiResponse;
import com.example.tradingcore.api.model.RiskAppetiteApiRequest;
import com.example.tradingcore.api.model.RiskAppetiteApiResponse;
import com.example.tradingcore.api.model.SafetyStateApiResponse;
import com.example.tradingcore.domain.service.TradingSurfaceService;
import com.example.tradingcore.mapping.TradingSurfaceMapperImpl;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Поверхность чтения торгового состояния и назначение чисел
 * риск-аппетита.
 *
 * <p><b>Что здесь проверяется по существу.</b> Наружу поверхность обязана
 * отдавать {@code internalId}, а не ключ БД: числовой ключ границу
 * сервиса не пересекает, и утёкший ключ становится частью контракта, из
 * которого его уже не убрать. Резолв этих идентичностей обязан идти
 * ОДНОЙ раскладкой на выборку: чтение на строку окна — запрос в цикле
 * длиной в окно, и растёт он вместе с историей счёта, а не с нагрузкой.
 * Назначение чисел риск-аппетита обязано ехать в домен маппером, а не
 * api-моделью вглубь: сервис api-модели не видит вовсе.
 *
 * <p>Маппер — НАСТОЯЩИЙ (сгенерированный MapStruct), не подменённый:
 * предмет половины проверок — именно перенос полей.
 */
class TradingSurfaceReadTest {

    private static final Long ACCOUNT_ID = 2L;
    private static final Long FIRST_INSTRUMENT_ID = 3L;
    private static final Long SECOND_INSTRUMENT_ID = 4L;
    private static final String ACCOUNT_INTERNAL_ID = "ea-0001";
    private static final String TENANT_INTERNAL_ID = "tn-0001";
    private static final String FIRST_INSTRUMENT_INTERNAL_ID = "in-0001";
    private static final String SECOND_INSTRUMENT_INTERNAL_ID = "in-0002";

    private final DealDataService deals = mock(DealDataService.class);
    private final DealTrancheDataService tranches = mock(DealTrancheDataService.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final InstrumentDataService instruments = mock(InstrumentDataService.class);
    private final AccountInstrumentStateDataService pairStates =
            mock(AccountInstrumentStateDataService.class);
    private final TenantRiskAppetiteDataService appetites = mock(TenantRiskAppetiteDataService.class);

    private final TradingSurfaceService service = new TradingSurfaceService(deals, tranches, accounts,
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
     * Торговое состояние счёта: ступень и статус едут строками, перечень
     * пар со стоящей ступенью — идентичностями.
     */
    @Test
    void theSafetyStateCarriesTheRungAndTheStandingPairs() {
        givenAccount();
        when(pairStates.findInstrumentIdsWithStandingRung(ACCOUNT_ID))
                .thenReturn(List.of(SECOND_INSTRUMENT_ID));
        when(instruments.findInternalIdsByIds(anyCollection()))
                .thenReturn(Map.of(SECOND_INSTRUMENT_ID, SECOND_INSTRUMENT_INTERNAL_ID));

        SafetyStateApiResponse response = controller.getSafetyState(ACCOUNT_INTERNAL_ID);

        assertThat(response.getExchangeAccountInternalId()).isEqualTo(ACCOUNT_INTERNAL_ID);
        assertThat(response.getAccountSafetyRung())
                .isEqualTo(ExchangeAccount.SafetyRung.HOLD.name());
        assertThat(response.getAccountStatus()).isEqualTo(ExchangeAccount.Status.ACTIVE.name());
        assertThat(response.getInstrumentInternalIdsWithStandingRung())
                .containsExactly(SECOND_INSTRUMENT_INTERNAL_ID);
    }

    /**
     * Назначение чисел: тело едет в домен маппером, идентичность тенанта —
     * путём вызова, а не телом.
     */
    @Test
    void assigningTheAppetiteMapsTheBodyIntoTheDomain() {
        RiskAppetiteApiRequest request = new RiskAppetiteApiRequest();
        request.setGlobalSimultaneousRiskPerDealPercent(new BigDecimal("1.5"));
        request.setGlobalConsecutiveLossLimit(3);
        when(appetites.applyRiskAppetite(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RiskAppetiteApiResponse response = controller.applyRiskAppetite(TENANT_INTERNAL_ID, request);

        assertThat(response.getTenantInternalId()).isEqualTo(TENANT_INTERNAL_ID);
        assertThat(response.getGlobalSimultaneousRiskPerDealPercent())
                .isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(response.getGlobalConsecutiveLossLimit()).isEqualTo(3);
    }

    /**
     * Строки риск-аппетита ещё нет — отказ адресный: «ядро о тенанте не
     * знает» обязано отличаться от «числа назначены пустыми».
     */
    @Test
    void aMissingAppetiteRowIsRefusedAsABadRequest() {
        when(appetites.findByTenantInternalId(TENANT_INTERNAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getRiskAppetite(TENANT_INTERNAL_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void givenAccount() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setStatus(ExchangeAccount.Status.ACTIVE);
        account.setSafetyRung(ExchangeAccount.SafetyRung.HOLD);
        account.setConsecutiveLossCount(1);
        account.setBlindPassCount(0);
        when(accounts.getRequiredByInternalId(ACCOUNT_INTERNAL_ID)).thenReturn(account);
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
