package com.example.tradingcore.unit.integration;

import com.example.testsupport.ServiceTokenProviderContract;
import com.example.tradingcore.exception.PeerReadException;
import com.example.tradingcore.integration.internal.api.ServiceTokenProvider;
import java.nio.file.Path;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Копия провайдера служебного токена в дереве {@code trading-core}
 * (`.claude/tests/cases/platform-shared-logic.md`, группы `U9`, `U12.2`,
 * клетка `U14.5`).
 */
class ServiceTokenProviderTest extends ServiceTokenProviderContract {

    @Override
    protected String tokenValue(OAuth2AuthorizedClientManager manager, String registrationId) {
        return new ServiceTokenProvider(manager).getTokenValue(registrationId);
    }

    @Override
    protected String expectedPrincipalName() {
        return "trading-core";
    }

    @Override
    protected Class<? extends RuntimeException> readExceptionType() {
        return PeerReadException.class;
    }

    @Override
    protected Path providerSource() {
        return Path.of("src", "main", "java", "com", "example", "tradingcore",
                "integration", "internal", "api", "ServiceTokenProvider.java");
    }
}
