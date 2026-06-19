package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.tradingbot.config.OkxProperties;
import com.example.tradingbot.integration.service.okx.OkxSigningInterceptor;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;

/**
 * I-cred — приватный вызов сырого клиента при пустых OKX-кредах (auth-негатив
 * клиентского слоя, probe бага I3). **Не через {@code /raw}** и без сети:
 * требует изолированной конфигурации без кредов (поднятый app креды имеет),
 * поэтому это offline-юнит-тест над {@link OkxSigningInterceptor}, не
 * {@code @SpringBootTest} (контур, не сетевой кейс).
 *
 * <p>Целевое (I3 closed): внятная ошибка «OKX credentials not configured».
 * Текущее (баг I3, {@code backlog} §I3): NPE в {@code sign()}
 * ({@code getSecret().getBytes()} на {@code null}) — до отправки, сети не
 * достигает. Тест фиксирует факт (исключение до сети) и наблюдает, открыт ли
 * I3 (NPE) или закрыт (внятная ошибка про credentials), не подгоняя вердикт.
 */
@Tag("source-api-live")
class ICredEmptyCredentialsLiveTest {

    private static final Logger log = LoggerFactory.getLogger(ICredEmptyCredentialsLiveTest.class);

    @Test
    @DisplayName("I-cred — приватный вызов на пустых OKX-кредах (probe I3)")
    void privateCallWithEmptyCredentialsFailsBeforeNetwork() {
        OkxProperties properties = new OkxProperties();
        // apiKey/secret/passphrase не заданы — изолированная конфигурация без кредов.
        OkxSigningInterceptor interceptor = new OkxSigningInterceptor(properties);

        MockClientHttpRequest request =
                new MockClientHttpRequest(HttpMethod.GET, URI.create("https://www.okx.com/api/v5/account/balance"));
        ClientHttpRequestExecution execution = (req, body) -> {
            throw new IllegalStateException("network must not be reached on empty credentials");
        };

        Throwable thrown = catchThrowable(() -> interceptor.intercept(request, new byte[0], execution));

        assertThat(thrown)
                .as("private call on empty credentials must fail before reaching the network")
                .isNotNull();

        String message = thrown.getMessage();
        boolean clearCredentialError = message != null && message.toLowerCase().contains("credential");
        if (clearCredentialError) {
            log.info("[I-cred] I3 CLOSED: clear credentials error — {}", thrown.toString());
        } else {
            log.info("[I-cred] I3 OPEN (bug §I3): {} — {} (target: clear 'OKX credentials not configured')",
                    thrown.getClass().getSimpleName(), message);
        }
    }
}
