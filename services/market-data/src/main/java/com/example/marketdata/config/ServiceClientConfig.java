package com.example.marketdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Исходящая сервисная идентичность: менеджер авторизованных клиентов для
 * межсервисного вызова без пользователя
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»).
 *
 * <p><b>Грант — client credentials, и другого здесь быть не может:</b>
 * пользовательского токена на этой границе не существует, а подставить
 * чужой значило бы выдать вызов сбора за действие человека.
 *
 * <p>Незаданная регистрация клиента означает, что сервис не поднимется:
 * это отказ, а не разрешение ходить к коннектору анонимно.
 */
@Configuration
public class ServiceClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }
}
