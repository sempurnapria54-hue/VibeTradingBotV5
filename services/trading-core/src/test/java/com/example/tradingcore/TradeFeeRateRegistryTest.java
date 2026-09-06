package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingcore.domain.service.TradeFeeRateSyncService;
import com.example.tradingcore.integration.exchange.ExchangeOperationsClient;
import com.example.tradingcore.mapping.InstrumentExternalRulesJsonConverter;
import com.example.tradingcore.mapping.TradeFeeRateMapper;
import com.example.tradingcore.mapping.TradeFeeRateMapperImpl;
import com.example.tradingcore.persistence.model.InstrumentEntity;
import com.example.tradingcore.persistence.model.TradeFeeRateEntity;
import com.example.tradingcore.persistence.repository.InstrumentRepository;
import com.example.tradingcore.persistence.repository.TradeFeeRateRepository;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.TradeFeeRateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Реестр ставок комиссии у владельца счёта
 * (docs/models/domain/other/TradeFeeRate.md): правило истории записи,
 * гидрация навеса правил инструмента и такт синка.
 *
 * <p><b>Ставка ключуется СЧЁТОМ, а не площадкой</b> — чтение приватное,
 * то есть требует ключей счёта, и комиссионный уровень принадлежит счёту.
 * Донорский ряд стоял на бирже; тест проверяет перенесённый радиус в
 * каждой из трёх троп.
 */
class TradeFeeRateRegistryTest {

    private static final OffsetDateTime OBSERVED_AT = OffsetDateTime.of(
            2026, 9, 6, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final Long ACCOUNT_ID = 7L;
    private static final String ACCOUNT_INTERNAL_ID = "ea-1";
    private static final String INSTRUMENT_TYPE = "SWAP";
    private static final String GROUP_ID = "1";

    private final TradeFeeRateRepository rateRepository = mock(TradeFeeRateRepository.class);
    private final TradeFeeRateMapper rateMapper = new TradeFeeRateMapperImpl();
    private final TradeFeeRateDataService rateDataService =
            new TradeFeeRateDataService(rateRepository, rateMapper);

    // --- правило истории записи -------------------------------------------

    /**
     * Значение группы не изменилось — подтверждаем последнюю строку на
     * месте: счётчик растёт, новая строка не заводится.
     */
    @Test
    void unchangedValueConfirmsTheLatestRowInPlace() {
        TradeFeeRateEntity stored = storedRow("0.0005", "0.0002", 4L);
        when(latestRows()).thenReturn(List.of(stored));

        rateDataService.record(observed("0.0005", "0.0002"));

        TradeFeeRateEntity saved = captureSaved();
        assertThat(saved).isSameAs(stored);
        assertThat(saved.getRefreshCount()).isEqualTo(5L);
        assertThat(saved.getExternalModifiedAt()).isEqualTo(OBSERVED_AT);
    }

    /** Значение изменилось — заводится новая строка с первым подтверждением. */
    @Test
    void changedValueStartsANewRow() {
        when(latestRows()).thenReturn(List.of(storedRow("0.0005", "0.0002", 4L)));

        rateDataService.record(observed("0.0008", "0.0002"));

        TradeFeeRateEntity saved = captureSaved();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getExternalTakerFeeRate()).isEqualTo("0.0008");
        assertThat(saved.getRefreshCount()).isEqualTo(1L);
        assertThat(saved.getExchangeAccountId()).isEqualTo(ACCOUNT_ID);
    }

    /**
     * Сравнение идёт по ЧИСЛАМ, записанным строками: сырое хранение без
     * объявленного предиката дало бы новую строку на каждое эквивалентное,
     * но иначе записанное значение.
     */
    @Test
    void equivalentlyWrittenValueIsStillTheSameValue() {
        TradeFeeRateEntity stored = storedRow("0.0005", "0.0002", 1L);
        when(latestRows()).thenReturn(List.of(stored));

        rateDataService.record(observed("0.00050", "0.000200"));

        assertThat(captureSaved()).isSameAs(stored);
    }

    // --- гидрация навеса ---------------------------------------------------

    /** Ставка наливается в навес по тройке «счёт, сырой тип, ключ группы». */
    @Test
    void navesseIsHydratedByTheAccountKeyedTriple() {
        InstrumentExternalRulesDataService rulesDataService = rulesDataService(rulesJson());
        when(latestRows()).thenReturn(List.of(storedRow("0.0005", "0.0002", 1L)));

        Optional<InstrumentExternalRules> rules = rulesDataService.findByInstrumentId(11L, ACCOUNT_ID);

        assertThat(rules).isPresent();
        assertThat(rules.get().takerFeeRate()).isEqualByComparingTo("0.0005");
    }

    /**
     * Счёт не назван — ставка не резолвится и НЕ подставляется: аксессор
     * отдаёт пустоту, а действие блокирует преконтроль.
     */
    @Test
    void withoutAnAccountTheRateStaysEmpty() {
        InstrumentExternalRulesDataService rulesDataService = rulesDataService(rulesJson());

        Optional<InstrumentExternalRules> rules = rulesDataService.findByInstrumentId(11L, null);

        assertThat(rules).isPresent();
        assertThat(rules.get().takerFeeRate()).isNull();
        verify(rateRepository, never())
                .findByExchangeAccountIdAndExternalInstrumentTypeAndExternalFeeGroupIdOrderByIdDesc(
                        any(), any(), any(), any());
    }

    /** Группа ещё не наблюдалась — то же: пустота нулём не подменяется. */
    @Test
    void anUnobservedGroupLeavesTheRateEmpty() {
        InstrumentExternalRulesDataService rulesDataService = rulesDataService(rulesJson());
        when(latestRows()).thenReturn(List.of());

        Optional<InstrumentExternalRules> rules = rulesDataService.findByInstrumentId(11L, ACCOUNT_ID);

        assertThat(rules.get().takerFeeRate()).isNull();
    }

    // --- такт синка --------------------------------------------------------

    /**
     * Вызов идёт на ПАРУ «счёт, тип инструмента», а не на инструмент:
     * ставка есть атрибут группы счёта, и вызов на каждый инструмент
     * размножал бы одно и то же значение.
     */
    @Test
    void oneCallPerAccountAndInstrumentTypeNotPerInstrument() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        TradeFeeRateSyncService syncService = syncService(client, List.of(INSTRUMENT_TYPE));
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenReturn(List.of(observedWithoutOwner("0.0005", "0.0002")));
        when(latestRows()).thenReturn(List.of());

        assertThat(syncService.synchronize()).isEqualTo(1);

        verify(client, times(1)).getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE);
        assertThat(captureSaved().getExchangeAccountId()).isEqualTo(ACCOUNT_ID);
    }

    /** Отказ одной пары стоит одну пару: соседний тип синхронизируется. */
    @Test
    void aFailingPairDoesNotStopTheNeighbouringOne() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        TradeFeeRateSyncService syncService = syncService(client, List.of(INSTRUMENT_TYPE, "FUTURES"));
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenThrow(new IllegalStateException("exchange refused"));
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, "FUTURES"))
                .thenReturn(List.of(observedWithoutOwner("0.0005", "0.0002")));
        when(latestRows()).thenReturn(List.of());

        assertThat(syncService.synchronize()).isEqualTo(1);
    }

    /** Каталог пуст — площадку не спрашиваем вовсе: спрашивать не о чем. */
    @Test
    void anEmptyCatalogueAsksTheExchangeNothing() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        TradeFeeRateSyncService syncService = syncService(client, List.of());

        assertThat(syncService.synchronize()).isEqualTo(0);

        verify(client, never()).getTradeFeeRates(any(), any());
    }

    // --- сборка состояния --------------------------------------------------

    private List<TradeFeeRateEntity> latestRows() {
        return rateRepository.findByExchangeAccountIdAndExternalInstrumentTypeAndExternalFeeGroupIdOrderByIdDesc(
                eq(ACCOUNT_ID), eq(INSTRUMENT_TYPE), eq(GROUP_ID), any());
    }

    private TradeFeeRateEntity captureSaved() {
        ArgumentCaptor<TradeFeeRateEntity> captor = ArgumentCaptor.forClass(TradeFeeRateEntity.class);
        verify(rateRepository).save(captor.capture());
        return captor.getValue();
    }

    private TradeFeeRateEntity storedRow(String taker, String maker, Long refreshCount) {
        TradeFeeRateEntity entity = new TradeFeeRateEntity();
        entity.setId(31L);
        entity.setExchangeAccountId(ACCOUNT_ID);
        entity.setExternalInstrumentType(INSTRUMENT_TYPE);
        entity.setExternalFeeGroupId(GROUP_ID);
        entity.setInstrumentType(InstrumentExternalRules.InstrumentType.SWAP.name());
        entity.setExternalTakerFeeRate(taker);
        entity.setExternalMakerFeeRate(maker);
        entity.setRefreshCount(refreshCount);
        return entity;
    }

    private TradeFeeRate observed(String taker, String maker) {
        TradeFeeRate rate = observedWithoutOwner(taker, maker);
        rate.setExchangeAccountId(ACCOUNT_ID);
        return rate;
    }

    private TradeFeeRate observedWithoutOwner(String taker, String maker) {
        TradeFeeRate rate = new TradeFeeRate();
        rate.setExternalInstrumentType(INSTRUMENT_TYPE);
        rate.setExternalFeeGroupId(GROUP_ID);
        rate.setInstrumentType(InstrumentExternalRules.InstrumentType.SWAP);
        rate.setExternalTakerFeeRate(taker);
        rate.setExternalMakerFeeRate(maker);
        rate.setExternalModifiedAt(OBSERVED_AT);
        return rate;
    }

    private String rulesJson() {
        return "{\"externalInstrumentType\":\"" + INSTRUMENT_TYPE + "\","
                + "\"externalFeeGroupId\":\"" + GROUP_ID + "\","
                + "\"externalContractValue\":\"0.1\",\"externalLotSize\":\"1\",\"externalMinSize\":\"1\"}";
    }

    private InstrumentExternalRulesDataService rulesDataService(String navesse) {
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        InstrumentEntity entity = new InstrumentEntity();
        entity.setId(11L);
        entity.setExternalRules(navesse);
        when(instrumentRepository.findById(11L)).thenReturn(Optional.of(entity));
        return new InstrumentExternalRulesDataService(instrumentRepository,
                new InstrumentExternalRulesJsonConverter(new com.fasterxml.jackson.databind.ObjectMapper()),
                rateDataService);
    }

    private TradeFeeRateSyncService syncService(ExchangeOperationsClient client, List<String> instrumentTypes) {
        ExchangeAccountDataService accountDataService = mock(ExchangeAccountDataService.class);
        InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
        when(accountDataService.findTradingAccounts()).thenReturn(List.of(tradingAccount()));
        when(instrumentDataService.findDistinctExternalTypes()).thenReturn(instrumentTypes);
        return new TradeFeeRateSyncService(accountDataService, instrumentDataService, rateDataService, client);
    }

    private ExchangeAccount tradingAccount() {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        account.setInternalId(ACCOUNT_INTERNAL_ID);
        account.setStatus(ExchangeAccount.Status.ACTIVE);
        account.setContour(ExchangeAccount.Contour.DEMO);
        return account;
    }
}
