package com.example.connector.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.connector.okx.config.CredentialsProperties;
import com.example.connector.okx.credentials.CachingExchangeCredentialsResolver;
import com.example.connector.okx.credentials.CredentialsUnavailableException;
import com.example.connector.okx.credentials.ExchangeCredentials;
import com.example.connector.okx.credentials.VaultExchangeCredentialsResolver;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Кэш ключей счёта на короткий срок.
 *
 * <p>Кэша требует архитектура ({@code docs/architecture/tenant-and-exchange.md}
 * §Ключи): без него каждая команда площадке ходила бы в хранилище, и его
 * недоступность останавливала бы торговлю мгновенно.
 */
class CachingExchangeCredentialsResolverTest {

    private static final String ACCOUNT = "acc-1";

    private final VaultExchangeCredentialsResolver delegate = mock(VaultExchangeCredentialsResolver.class);

    @Test
    void secondCallWithinTermDoesNotReachTheStore() {
        when(delegate.resolve(ACCOUNT)).thenReturn(credentials());
        CachingExchangeCredentialsResolver resolver = resolver(Duration.ofMinutes(1));

        assertThat(resolver.resolve(ACCOUNT)).isNotNull();
        assertThat(resolver.resolve(ACCOUNT)).isNotNull();

        verify(delegate, times(1)).resolve(ACCOUNT);
    }

    /**
     * Точка, в которую придёт подписчик события ротации: после сброса
     * ключи перечитываются, не дожидаясь срока.
     */
    @Test
    void evictionForcesReread() {
        when(delegate.resolve(ACCOUNT)).thenReturn(credentials());
        CachingExchangeCredentialsResolver resolver = resolver(Duration.ofMinutes(1));

        resolver.resolve(ACCOUNT);
        resolver.evict(ACCOUNT);
        resolver.resolve(ACCOUNT);

        verify(delegate, times(2)).resolve(ACCOUNT);
    }

    /** Истёкшая запись перечитывается: срок и есть механизм устаревания. */
    @Test
    void expiredEntryIsReread() throws Exception {
        when(delegate.resolve(ACCOUNT)).thenReturn(credentials());
        CachingExchangeCredentialsResolver resolver = resolver(Duration.ofMillis(20));

        resolver.resolve(ACCOUNT);
        Thread.sleep(40);
        resolver.resolve(ACCOUNT);

        verify(delegate, times(2)).resolve(ACCOUNT);
    }

    /** Нулевой срок выключает кэш целиком. */
    @Test
    void zeroTermDisablesTheCache() {
        when(delegate.resolve(ACCOUNT)).thenReturn(credentials());
        CachingExchangeCredentialsResolver resolver = resolver(Duration.ZERO);

        resolver.resolve(ACCOUNT);
        resolver.resolve(ACCOUNT);

        verify(delegate, times(2)).resolve(ACCOUNT);
    }

    /**
     * Отказ не кэшируется: иначе заведение ключей вступало бы в силу
     * через срок кэша, и регистрация счёта выглядела бы сломанной.
     */
    @Test
    void refusalIsNotCached() {
        when(delegate.resolve(ACCOUNT))
                .thenThrow(new CredentialsUnavailableException(ACCOUNT))
                .thenReturn(credentials());
        CachingExchangeCredentialsResolver resolver = resolver(Duration.ofMinutes(1));

        assertThatThrownBy(() -> resolver.resolve(ACCOUNT))
                .isInstanceOf(CredentialsUnavailableException.class);
        assertThat(resolver.resolve(ACCOUNT)).isNotNull();
    }

    private CachingExchangeCredentialsResolver resolver(Duration ttl) {
        CredentialsProperties properties = new CredentialsProperties();
        properties.setCacheTtl(ttl);
        return new CachingExchangeCredentialsResolver(delegate, properties);
    }

    private ExchangeCredentials credentials() {
        return new ExchangeCredentials("key", "secret", "pass", ExchangeAccount.Contour.DEMO);
    }
}
