package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.domain.service.RegistryProjectionService;
import com.example.tradingcore.integration.AuthReadClient;
import com.example.tradingcore.integration.MarketDataReadClient;
import com.example.tradingcore.integration.PeerReadException;
import com.example.tradingcore.integration.PeerServiceUnavailableException;
import com.example.tradingcore.integration.model.InstrumentMarketDataResponse;
import com.example.tradingcore.mapping.ExchangeAccountMapper;
import com.example.tradingcore.mapping.ExchangeAccountMapperImpl;
import com.example.tradingcore.mapping.InstrumentMapper;
import com.example.tradingcore.mapping.InstrumentMapperImpl;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.InstrumentDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Проход синка каталога различает два исхода отказа.
 *
 * <p>Недоступность владельца прекращает проход целиком — следующие сотни
 * вызовов дадут тот же отказ; отказ по ОДНОЙ строке проход не роняет, а
 * строка остаётся со старым моментом снимка, то есть сама себя показывает
 * гейту свежести (docs/rules/runtime-error-classification.md §«Отказ
 * соседа по ярусу — свой класс, и сделку в ошибку он не уводит»).
 *
 * <p>Тест держит и третье условие — <b>метка не двигается без правил</b>:
 * упади чтение правил, и запись строки не происходит вовсе, иначе один
 * {@code projected_at} описывал бы свежую спецификацию при неизвестных
 * правилах.
 */
class InstrumentProjectionPassTest {

    private static final OffsetDateTime PROJECTED_AT = OffsetDateTime.of(
            2026, 9, 5, 12, 0, 0, 0, ZoneOffset.UTC);

    private final AuthReadClient authReadClient = mock(AuthReadClient.class);
    private final MarketDataReadClient marketDataReadClient = mock(MarketDataReadClient.class);
    private final ExchangeAccountDataService accountDataService = mock(ExchangeAccountDataService.class);
    private final InstrumentDataService instrumentDataService = mock(InstrumentDataService.class);
    private final TenantRiskAppetiteDataService riskAppetiteDataService =
            mock(TenantRiskAppetiteDataService.class);
    private final ExchangeAccountMapper accountMapper = new ExchangeAccountMapperImpl();
    private final InstrumentMapper instrumentMapper = new InstrumentMapperImpl();

    private final RegistryProjectionService service = new RegistryProjectionService(
            authReadClient, marketDataReadClient, accountDataService, instrumentDataService,
            riskAppetiteDataService, accountMapper, instrumentMapper);

    @Test
    void ownerUnavailableStopsThePass() {
        when(marketDataReadClient.getInstruments()).thenReturn(List.of(
                listed("i-1"), listed("i-2"), listed("i-3")));
        when(marketDataReadClient.getInstrumentRules("i-1")).thenReturn(new InstrumentExternalRules());
        when(marketDataReadClient.getInstrumentRules("i-2"))
                .thenThrow(new PeerServiceUnavailableException("owner is down", null));

        Integer projected = service.synchronizeInstruments(PROJECTED_AT);

        assertThat(projected).isEqualTo(1);
        verify(marketDataReadClient, never()).getInstrumentRules("i-3");
    }

    @Test
    void singleRowRefusalDoesNotStopThePass() {
        when(marketDataReadClient.getInstruments()).thenReturn(List.of(
                listed("i-1"), listed("i-2"), listed("i-3")));
        when(marketDataReadClient.getInstrumentRules(anyString())).thenReturn(new InstrumentExternalRules());
        when(marketDataReadClient.getInstrumentRules("i-2"))
                .thenThrow(new PeerReadException("owner refused this one"));

        Integer projected = service.synchronizeInstruments(PROJECTED_AT);

        assertThat(projected).isEqualTo(2);
        verify(marketDataReadClient).getInstrumentRules("i-3");
    }

    @Test
    void markDoesNotMoveWhenRulesWereNotRead() {
        when(marketDataReadClient.getInstruments()).thenReturn(List.of(listed("i-1")));
        when(marketDataReadClient.getInstrumentRules("i-1"))
                .thenThrow(new PeerReadException("rules read failed"));

        service.synchronizeInstruments(PROJECTED_AT);

        verify(instrumentDataService, never()).upsertProjection(any(), any(), eq(PROJECTED_AT));
    }

    /**
     * Пустой навес — не отказ: у владельца он может быть ещё не
     * материализован, и строка кладётся с пустыми правилами.
     */
    @Test
    void absentRulesAreProjectedAsEmpty() {
        when(marketDataReadClient.getInstruments()).thenReturn(List.of(listed("i-1")));
        when(marketDataReadClient.getInstrumentRules("i-1")).thenReturn(null);

        Integer projected = service.synchronizeInstruments(PROJECTED_AT);

        assertThat(projected).isEqualTo(1);
        verify(instrumentDataService).upsertProjection(any(), eq(null), eq(PROJECTED_AT));
    }

    private InstrumentMarketDataResponse listed(String internalId) {
        InstrumentMarketDataResponse response = new InstrumentMarketDataResponse();
        response.setInternalId(internalId);
        response.setExchangeCode("OKX");
        response.setExternalId(internalId + "-USD-SWAP");
        response.setExternalType("SWAP");
        response.setStatus("ACTIVE");
        return response;
    }
}
