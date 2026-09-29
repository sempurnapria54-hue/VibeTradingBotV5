package com.example.marketdata.integration.internal.api;

import static java.util.Objects.isNull;

import com.example.marketdata.config.ConnectorProperties;
import com.example.marketdata.exception.ExchangeAccessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;

/**
 * Добывает токен СЕРВИСНОЙ идентичности кластера для исходящего вызова.
 *
 * <p>Межсервисный вызов идёт без пользователя, и контекст тенанта он
 * несёт операндом, а не токеном
 * ({@code docs/architecture/contracts.md} §«Контекст тенанта в вызове»).
 * У чтений рыночных данных тенанта нет вовсе: листинг и свечи —
 * платформенное знание.
 *
 * <p><b>Пустой токен — отказ, а не анонимный вызов.</b> Коннектор закрыт
 * по умолчанию, и уйти к нему без токена значило бы получить отказ на
 * его стороне с причиной, неотличимой от «ключи отвергнуты».
 *
 * <p><b>Всякий отказ добычи — отказ доступа</b>
 * ({@link ExchangeAccessException}; docs/architecture/services/market-data.md
 * §«Какие вызовы делает и какие принимает»): недобытая идентичность
 * одинакова для всех инструментов прохода, и проход она прекращает.
 * Класс библиотеки наружу не уходит — по нему вызывающий не ветвится.
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenProvider {

    /** Имя принципала авторизованного клиента: сервис, а не человек. */
    private static final String PRINCIPAL_NAME = "market-data";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final ConnectorProperties properties;

    /** Значение bearer-токена для вызова коннектора. */
    public String getTokenValue() {
        OAuth2AuthorizedClient client = authorize();
        if (isNull(client)) {
            throw new ExchangeAccessException("Service identity token is not available for connector call");
        }
        return client.getAccessToken().getTokenValue();
    }

    /** Запрос выдачи; отказ библиотеки переводится в класс отказа доступа. */
    private OAuth2AuthorizedClient authorize() {
        try {
            return authorizedClientManager.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId(properties.getClientRegistrationId())
                    .principal(PRINCIPAL_NAME)
                    .build());
        } catch (OAuth2AuthorizationException | IllegalArgumentException e) {
            throw new ExchangeAccessException("Service identity token is not available for connector call", e);
        }
    }
}
