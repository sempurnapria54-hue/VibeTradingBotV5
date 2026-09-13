package com.example.platform.client;

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
 * вызов идёт под сервисной идентичностью — по расписанию либо служебным
 * чтением, — а контекст тенанта едет операндом вызова; подставить
 * пользовательский токен значило бы выдать служебное чтение за действие
 * человека.
 *
 * <p>Незаданная регистрация клиента означает, что сервис не поднимется:
 * это отказ, а не разрешение ходить к соседям анонимно.
 *
 * <p><b>Берётся ИМЕНОВАННО, через {@code @Import}, а не сканированием.</b>
 * Класс требует {@code spring-security-oauth2-client}, который объявлен у
 * тех сервисов, кто к соседям и ходит; у прочих его нет, и попадание этой
 * конфигурации в область сканирования уронило бы их старт.
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
