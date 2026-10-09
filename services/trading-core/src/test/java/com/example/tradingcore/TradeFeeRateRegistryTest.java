package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingcore.config.TradeFeeRateSyncProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.domain.service.TradeFeeRateSyncService;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
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
import com.example.tradingcore.util.Constants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Реестр ставок комиссии у владельца счёта
 * (docs/models/domain/other/TradeFeeRate.md): правило истории записи,
 * гидрация навеса правил инструмента, такт синка и детектор несвежести
 * ставки (docs/rules/instrument-hold.md §«Несвежесть ставки комиссии»).
 *
 * <p><b>Несвежесть собирается настоящим состоянием:</b> строка ставки с
 * моментом подтверждения, навес инструмента с ключом группы, ответ
 * площадки. Подменён только исполнитель блокировки — коллаборатор со своей
 * проверкой.
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
    private static final String OTHER_GROUP_ID = "2";
    private static final Long INSTRUMENT_ID = 21L;
    private static final Long OTHER_INSTRUMENT_ID = 22L;

    private final TradeFeeRateRepository rateRepository = mock(TradeFeeRateRepository.class);
    private final TradeFeeRateMapper rateMapper = new TradeFeeRateMapperImpl();
    private final TradeFeeRateDataService rateDataService =
            new TradeFeeRateDataService(rateRepository, rateMapper);
    private final HoldService holdService = mock(HoldService.class);

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

    // --- детектор несвежести ------------------------------------------------

    /**
     * Группа пропала из ответа: её строка не подтверждена дольше порога, и
     * мягкая ступень ложится на инструменты ЭТОЙ группы; группа, которую
     * площадка подтвердила, свежа, и её инструмент не тронут.
     */
    @Test
    void aGroupMissingFromTheAnswerHoldsEntriesOnItsOwnInstrumentsOnly() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenReturn(List.of(observedAt(GROUP_ID, now())));
        when(latestRows(GROUP_ID)).thenReturn(List.of(storedRowAt(GROUP_ID, now().minusHours(40))));
        when(latestRows(OTHER_GROUP_ID)).thenReturn(List.of(storedRowAt(OTHER_GROUP_ID, now().minusHours(40))));

        detectingSyncService(client).synchronize();

        DealContext held = heldContext();
        assertThat(held.getInstrument().getId()).isEqualTo(OTHER_INSTRUMENT_ID);
        assertThat(held.getExchangeAccount().getId()).isEqualTo(ACCOUNT_ID);
    }

    /**
     * Вызов типа не прошёл целиком: не подтверждена ни одна группа, и
     * ступень ложится на все инструменты этого типа — радиус выведен тем же
     * операндом, а не назначен.
     */
    @Test
    void aFailedCallHoldsEntriesOnEveryInstrumentOfTheType() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenThrow(new IllegalStateException("exchange refused"));
        when(latestRows(GROUP_ID)).thenReturn(List.of(storedRowAt(GROUP_ID, now().minusHours(40))));
        when(latestRows(OTHER_GROUP_ID)).thenReturn(List.of(storedRowAt(OTHER_GROUP_ID, now().minusHours(40))));

        detectingSyncService(client).synchronize();

        ArgumentCaptor<DealContext> contexts = ArgumentCaptor.forClass(DealContext.class);
        verify(holdService, times(2)).raise(eq(staleSignal()), contexts.capture());
        assertThat(contexts.getAllValues().stream()
                .map(context -> context.getInstrument().getId())
                .collect(Collectors.toList()))
                .containsExactlyInAnyOrder(INSTRUMENT_ID, OTHER_INSTRUMENT_ID);
    }

    /** Площадка подтвердила обе группы этим тиком — несвежего нет, ступени нет. */
    @Test
    void confirmedGroupsHoldNothing() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenReturn(List.of(observedAt(GROUP_ID, now()), observedAt(OTHER_GROUP_ID, now())));
        when(latestRows(GROUP_ID)).thenReturn(List.of(storedRowAt(GROUP_ID, now().minusHours(40))));
        when(latestRows(OTHER_GROUP_ID)).thenReturn(List.of(storedRowAt(OTHER_GROUP_ID, now().minusHours(40))));

        detectingSyncService(client).synchronize();

        verify(holdService, never()).raise(any(), any());
    }

    /**
     * Строка моложе порога свежа и без подтверждения этим тиком: одиночный
     * пропущенный такт ступени не поднимает.
     */
    @Test
    void aRowYoungerThanTheThresholdIsFreshWithoutThisTicksConfirmation() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenThrow(new IllegalStateException("exchange refused"));
        when(latestRows(GROUP_ID)).thenReturn(List.of(storedRowAt(GROUP_ID, now().minusHours(7))));
        when(latestRows(OTHER_GROUP_ID)).thenReturn(List.of(storedRowAt(OTHER_GROUP_ID, now().minusHours(7))));

        detectingSyncService(client).synchronize();

        verify(holdService, never()).raise(any(), any());
    }

    /**
     * Группа не наблюдалась вовсе — несвежего числа нет, и ступени нет:
     * действие с пустой ставкой отвергает преконтроль своим кодом.
     */
    @Test
    void aNeverObservedGroupIsNotThisTrigger() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE)).thenReturn(List.of());
        when(latestRows(GROUP_ID)).thenReturn(List.of());
        when(latestRows(OTHER_GROUP_ID)).thenReturn(List.of());

        detectingSyncService(client).synchronize();

        verify(holdService, never()).raise(any(), any());
    }

    /** Момент подтверждения пуст — свежесть не измерена, строка несвежа. */
    @Test
    void anUnmeasuredConfirmationMomentReadsAsStale() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenReturn(List.of(observedAt(OTHER_GROUP_ID, now())));
        when(latestRows(GROUP_ID)).thenReturn(List.of(storedRowAt(GROUP_ID, null)));
        when(latestRows(OTHER_GROUP_ID)).thenReturn(List.of(storedRowAt(OTHER_GROUP_ID, now().minusHours(40))));

        detectingSyncService(client).synchronize();

        assertThat(heldContext().getInstrument().getId()).isEqualTo(INSTRUMENT_ID);
    }

    /**
     * Внешний снимок отчёта — ставка группы, какой её видел детектор, и
     * порог: позиции и заявки инструмента к основанию отношения не имеют,
     * и площадку за ними отчёт не читает.
     */
    @Test
    void theHoldCarriesTheObservedRateAsTheExternalObservation() {
        ExchangeOperationsClient client = mock(ExchangeOperationsClient.class);
        when(client.getTradeFeeRates(ACCOUNT_INTERNAL_ID, INSTRUMENT_TYPE))
                .thenReturn(List.of(observedAt(GROUP_ID, now())));
        when(latestRows(GROUP_ID)).thenReturn(List.of(storedRowAt(GROUP_ID, now())));
        when(latestRows(OTHER_GROUP_ID)).thenReturn(List.of(storedRowAt(OTHER_GROUP_ID, now().minusHours(40))));

        detectingSyncService(client).synchronize();

        assertThat(heldContext().getExternalObservation())
                .containsEntry("instrumentExternalId", "ETH-USDT-SWAP")
                .containsEntry("externalFeeGroupId", OTHER_GROUP_ID)
                .containsEntry("externalInstrumentType", INSTRUMENT_TYPE)
                .containsEntry("freshnessThreshold", "PT27H");
    }

    // --- сборка состояния --------------------------------------------------

    private List<TradeFeeRateEntity> latestRows() {
        return latestRows(GROUP_ID);
    }

    private List<TradeFeeRateEntity> latestRows(String groupId) {
        return rateRepository.findByExchangeAccountIdAndExternalInstrumentTypeAndExternalFeeGroupIdOrderByIdDesc(
                eq(ACCOUNT_ID), eq(INSTRUMENT_TYPE), eq(groupId), any());
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** Единственный затребованный подъём: сигнал несвежести и его контекст. */
    private DealContext heldContext() {
        ArgumentCaptor<DealContext> captor = ArgumentCaptor.forClass(DealContext.class);
        verify(holdService).raise(eq(staleSignal()), captor.capture());
        return captor.getValue();
    }

    private static HoldSignal staleSignal() {
        return HoldSignal.instrumentSoft(Constants.Hold.INSTRUMENT_FEE_RATE_STALE);
    }

    /** Строка группы с моментом последнего подтверждения источником. */
    private TradeFeeRateEntity storedRowAt(String groupId, OffsetDateTime confirmedAt) {
        TradeFeeRateEntity entity = storedRow("0.0005", "0.0002", 3L);
        entity.setExternalFeeGroupId(groupId);
        entity.setExternalModifiedAt(confirmedAt);
        return entity;
    }

    /** Наблюдение группы с моментом ответа источника. */
    private TradeFeeRate observedAt(String groupId, OffsetDateTime answeredAt) {
        TradeFeeRate rate = observedWithoutOwner("0.0005", "0.0002");
        rate.setExternalFeeGroupId(groupId);
        rate.setExternalModifiedAt(answeredAt);
        return rate;
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
        return rulesJson(GROUP_ID);
    }

    private String rulesJson(String groupId) {
        return "{\"externalInstrumentType\":\"" + INSTRUMENT_TYPE + "\","
                + "\"externalFeeGroupId\":\"" + groupId + "\","
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
        return new TradeFeeRateSyncService(accountDataService, instrumentDataService,
                mock(InstrumentExternalRulesDataService.class), rateDataService, client, holdService,
                new TradeFeeRateSyncProperties());
    }

    /**
     * Синк с контуром из двух инструментов одного типа в РАЗНЫХ
     * комиссионных группах: {@code BTC-USDT-SWAP} — группа «1»,
     * {@code ETH-USDT-SWAP} — группа «2». Навесы настоящие, чтение
     * проекции подменено.
     *
     * <p><b>Контур отдаётся ДВУМЯ страницами</b> по инструменту: второй
     * инструмент лежит за первой страницей, и всякая клетка, ждущая на нём
     * ступени, заодно мерит, что детектор обходит контур до конца, а не
     * отрезает хвост окном. Навесы читаются пачкой на страницу — стаб
     * репозитория отдаёт ровно запрошенные строки.
     */
    private TradeFeeRateSyncService detectingSyncService(ExchangeOperationsClient client) {
        ExchangeAccountDataService accountDataService = mock(ExchangeAccountDataService.class);
        InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        when(accountDataService.findTradingAccounts()).thenReturn(List.of(tradingAccount()));
        when(instrumentDataService.findDistinctExternalTypes()).thenReturn(List.of(INSTRUMENT_TYPE));
        doAnswer(invocation -> {
            Consumer<List<Instrument>> pageConsumer = invocation.getArgument(2);
            pageConsumer.accept(List.of(contourInstrument(INSTRUMENT_ID, "BTC-USDT-SWAP")));
            pageConsumer.accept(List.of(contourInstrument(OTHER_INSTRUMENT_ID, "ETH-USDT-SWAP")));
            return null;
        }).when(instrumentDataService).forEachContourPage(any(), any(), any());
        Map<Long, InstrumentEntity> projection = Map.of(
                INSTRUMENT_ID, instrumentRow(INSTRUMENT_ID, rulesJson(GROUP_ID)),
                OTHER_INSTRUMENT_ID, instrumentRow(OTHER_INSTRUMENT_ID, rulesJson(OTHER_GROUP_ID)));
        when(instrumentRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> requested = invocation.getArgument(0);
            List<InstrumentEntity> found = new ArrayList<>();
            requested.forEach(id -> found.add(projection.get(id)));
            return found;
        });
        InstrumentExternalRulesDataService rulesDataService = new InstrumentExternalRulesDataService(
                instrumentRepository,
                new InstrumentExternalRulesJsonConverter(new com.fasterxml.jackson.databind.ObjectMapper()),
                rateDataService);
        return new TradeFeeRateSyncService(accountDataService, instrumentDataService, rulesDataService,
                rateDataService, client, holdService, new TradeFeeRateSyncProperties());
    }

    private static Instrument contourInstrument(Long id, String externalId) {
        Instrument instrument = new Instrument();
        instrument.setId(id);
        instrument.setExternalId(externalId);
        return instrument;
    }

    private static InstrumentEntity instrumentRow(Long id, String navesse) {
        InstrumentEntity entity = new InstrumentEntity();
        entity.setId(id);
        entity.setExternalRules(navesse);
        return entity;
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
