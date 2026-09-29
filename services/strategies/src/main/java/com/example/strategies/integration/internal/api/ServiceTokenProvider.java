package com.example.strategies.integration.internal.api;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.platform.exception.PeerServiceUnavailableException;
import com.example.strategies.exception.PeerReadException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
 *
 * <p><b>Отказ добычи приходит классом соседского яруса, а не классом
 * библиотеки</b> (docs/rules/runtime-error-classification.md §«Добыча
 * служебного токена — часть вызова соседа»): провайдер идентичности не
 * ответил — недоступность; отверг выдачу, регистрация не настроена либо
 * клиента не выдано — осознанный отказ, то есть наш дефект.
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenProvider {

    /** Имя принципала авторизованного клиента: сервис, а не человек. */
    private static final String PRINCIPAL_NAME = "strategies";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    /** Значение bearer-токена под регистрацией клиента соседа. */
    public String getTokenValue(String clientRegistrationId) {
        OAuth2AuthorizedClient client = authorize(clientRegistrationId);
        if (isNull(client)) {
            throw new PeerReadException(
                    "Service identity token is not available for registration " + clientRegistrationId);
        }
        return client.getAccessToken().getTokenValue();
    }

    /** Запрос выдачи; отказ библиотеки переводится в класс своей природы. */
    private OAuth2AuthorizedClient authorize(String clientRegistrationId) {
        try {
            return authorizedClientManager.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientRegistrationId)
                    .principal(PRINCIPAL_NAME)
                    .build());
        } catch (OAuth2AuthorizationException e) {
            if (isTrue(isUnavailable(e))) {
                throw new PeerServiceUnavailableException(
                        "Identity provider is unavailable for registration " + clientRegistrationId, e);
            }
            throw new PeerReadException(
                    "Service identity token is refused for registration " + clientRegistrationId, e);
        } catch (IllegalArgumentException e) {
            throw new PeerReadException(
                    "Service identity is not configured for registration " + clientRegistrationId, e);
        }
    }

    /**
     * Провайдер идентичности не ответил: транспорт либо {@code 5xx} его
     * точки токенов. Библиотека несёт такой отказ причиной своего класса.
     */
    private static Boolean isUnavailable(OAuth2AuthorizationException failure) {
        for (Throwable cause = failure.getCause(); nonNull(cause); cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                return response.getStatusCode().is5xxServerError();
            }
            if (cause instanceof RestClientException) {
                return true;
            }
        }
        return false;
    }
}
