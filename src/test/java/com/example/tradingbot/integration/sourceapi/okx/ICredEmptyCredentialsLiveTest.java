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
 * <p>I3 закрыт ({@code backlog} §I3): приватный вызов на пустых кредах
 * fail-fast'ит внятной {@link IllegalStateException} «OKX credentials not
 * configured» до подписи и сети — не голый NPE ({@code getSecret().getBytes()}
 * на {@code null}). Тест ждёт это поведение.
 */
@Tag("source-api-live")
class ICredEmptyCredentialsLiveTest {

    private static final Logger log = LoggerFactory.getLogger(ICredEmptyCredentialsLiveTest.class);

    @Test
    @DisplayName("I-cred — приватный вызов на пустых OKX-кредах: внятная ошибка до сети (I3 closed)")
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

        // I3 closed: fail-fast внятной ошибкой про credentials ДО сети — не голый
        // NPE и не достижение сети (network-stub бросил бы иное сообщение).
        assertThat(thrown)
                .as("private call on empty credentials must fail fast with a clear credentials error before the network")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OKX credentials not configured");
        log.info("[I-cred] I3 CLOSED: {}", thrown.toString());
    }
}
