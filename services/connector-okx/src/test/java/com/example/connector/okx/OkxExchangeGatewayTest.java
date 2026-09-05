package com.example.connector.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.credentials.ExchangeCredentialsResolver;
import com.example.connector.okx.gateway.OkxExchangeGateway;
import com.example.connector.okx.mapping.AlgoOrderMapper;
import com.example.connector.okx.mapping.BalanceContainerMapper;
import com.example.connector.okx.mapping.CandleMapper;
import com.example.connector.okx.mapping.DealCashFlowMapper;
import com.example.connector.okx.mapping.InstrumentExternalRulesMapper;
import com.example.connector.okx.mapping.InstrumentMapper;
import com.example.connector.okx.mapping.MarketPriceDataMapper;
import com.example.connector.okx.mapping.MarketSnapshotMapper;
import com.example.connector.okx.mapping.OrderMapper;
import com.example.connector.okx.mapping.PositionMapper;
import com.example.connector.okx.mapping.TradeFeeRateMapper;
import com.example.connector.okx.source.OkxSourceReader;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Граница сервиса: резолв ключей по счёту и перевод снапшота в общую
 * модель.
 *
 * <p><b>Проверяется то, что отличает коннектор от донорской границы</b> —
 * ключи берутся на КАЖДЫЙ приватный вызов и не берутся вовсе на
 * публичном, а пустой ответ источника остаётся пустотой, а не превращается
 * в отказ ({@code ExchangeGateway} §«Контракт чтения»).
 */
class OkxExchangeGatewayTest {

    private static final String ACCOUNT = "acc-1";
    private static final String OTHER_ACCOUNT = "acc-2";
    private static final String INSTRUMENT = "BTC-USDT-SWAP";

    private final OkxSourceReader reader = mock(OkxSourceReader.class);
    private final ExchangeCredentialsResolver resolver = mock(ExchangeCredentialsResolver.class);
    private final OkxExchangeGateway gateway = new OkxExchangeGateway(
            reader, resolver,
            mock(OrderMapper.class), mock(AlgoOrderMapper.class), mock(PositionMapper.class),
            mock(InstrumentMapper.class), mock(InstrumentExternalRulesMapper.class),
            mock(BalanceContainerMapper.class), mock(CandleMapper.class), mock(DealCashFlowMapper.class),
            mock(TradeFeeRateMapper.class), mock(MarketPriceDataMapper.class),
            mock(MarketSnapshotMapper.class));

    /**
     * Ключи резолвятся на каждом вызове и по тому счёту, который в вызове
     * назван: закэшируй их полем — и второй счёт подписался бы ключами
     * первого.
     */
    @Test
    void keysAreResolvedPerCallForTheNamedAccount() {
        when(resolver.resolve(any())).thenReturn(credentials());

        gateway.getPositions(ACCOUNT);
        gateway.getPositions(ACCOUNT);
        gateway.getPositions(OTHER_ACCOUNT);

        verify(resolver, times(2)).resolve(ACCOUNT);
        verify(resolver, times(1)).resolve(OTHER_ACCOUNT);
    }

    /**
     * Публичное чтение счёта не требует: связать листинг с наличием счёта
     * значило бы запретить сбор рыночных данных без зарегистрированного
     * счёта.
     */
    @Test
    void publicReadNeedsNoAccount() {
        gateway.getServerTime();
        gateway.getInstruments("SWAP");
        gateway.getMarketPriceData(INSTRUMENT);

        verify(resolver, never()).resolve(any());
    }

    /** «Не найдено» — пустота, а не отказ. */
    @Test
    void absentSingleResultStaysEmpty() {
        when(resolver.resolve(ACCOUNT)).thenReturn(credentials());
        when(reader.getPosition(any(), any())).thenReturn(null);

        assertThat(gateway.getPosition(ACCOUNT, INSTRUMENT)).isNull();
    }

    /**
     * Пустой перечень остаётся пустым перечнем, а не {@code null}: иначе
     * каждый читатель обязан был бы проверять пустоту сам, и первый
     * забывший получил бы NPE вместо «ничего не найдено».
     */
    @Test
    void absentListBecomesEmptyList() {
        when(resolver.resolve(ACCOUNT)).thenReturn(credentials());
        when(reader.getPositions(any())).thenReturn(null);
        when(reader.getBills(any(), any(), any())).thenReturn(null);

        assertThat(gateway.getPositions(ACCOUNT)).isEmpty();
        assertThat(gateway.getBills(ACCOUNT, OffsetDateTime.now(), OffsetDateTime.now())).isEmpty();
    }

    private ExchangeCredentials credentials() {
        return new ExchangeCredentials("key", "secret", "pass", ExchangeAccount.Contour.DEMO);
    }
}
