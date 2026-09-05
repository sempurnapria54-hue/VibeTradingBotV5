package com.example.tradingcore.integration;

import static java.util.Objects.isNull;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

/**
 * Добывает токен СЕРВИСНОЙ идентичности кластера для исходящего вызова.
 *
 * <p>Межсервисный вызов идёт без пользователя, и контекст тенанта он
 * несёт операндом, а не токеном
 * (docs/architecture/contracts.md §«Контекст тенанта в вызове»). У тика
 * синка проекций человека нет вовсе: он идёт по расписанию.
 *
 * <p><b>Пустой токен — отказ, а не анонимный вызов.</b> Соседи закрыты по
 * умолчанию, и уйти к ним без токена значило бы получить отказ на их
 * стороне с причиной, неотличимой от «идентичность отвергнута».
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenProvider {

    /** Имя принципала авторизованного клиента: сервис, а не человек. */
    private static final String PRINCIPAL_NAME = "trading-core";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    /** Значение bearer-токена под регистрацией клиента соседа. */
    public String getTokenValue(String clientRegistrationId) {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(PRINCIPAL_NAME)
                .build();
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (isNull(client) || isNull(client.getAccessToken())) {
            throw new PeerReadException(
                    "Service identity token is not available for registration " + clientRegistrationId);
        }
        return client.getAccessToken().getTokenValue();
    }
}
