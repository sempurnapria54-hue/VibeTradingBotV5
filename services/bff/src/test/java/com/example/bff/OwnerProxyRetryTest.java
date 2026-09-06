package com.example.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.bff.config.PerimeterProperties;
import com.example.bff.integration.OwnerAddressResolver;
import com.example.bff.integration.OwnerProxyClient;
import com.example.bff.integration.PeerServiceUnavailableException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Периметр повторяет ТОЛЬКО чтения
 * (docs/architecture/contracts.md §«Периметр повторяет только чтения»).
 *
 * <p><b>Почему это охраняется прогоном.</b> Клейм асимметричен и его
 * половина невидима при чтении кода: «повторяет чтения» проверяется
 * успехом, а «не повторяет команды» — только счётом попыток. Ключа
 * идемпотентности с энфорсером у владельцев сегодня нет, и повторённая
 * пересылка команды была бы командой, выполненной дважды.
 */
class OwnerProxyRetryTest {

    private static final String OWNER = "trading-core";
    private static final String PATH = "/api/v1/trading-core/deals";
    private static final String URL = "http://trading-core:8080" + PATH;

    @Test
    @DisplayName("Чтение повторяется в пределах бюджета и доходит со второй попытки")
    void aReadIsRetriedWithinItsBudget() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("connection refused")));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ResponseEntity<byte[]> answer = clientOver(builder, 1)
                .forward(OWNER, HttpMethod.GET, PATH, new HttpHeaders(), null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        server.verify();
    }

    /**
     * Мутирующий запрос не повторяется ни разу: его исход возвращается
     * вызывающему как есть, а перечитать состояние — работа браузера.
     */
    @Test
    @DisplayName("Мутирующий запрос не повторяется: попытка ровно одна")
    void aMutatingRequestIsNeverRetried() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("connection refused")));

        OwnerProxyClient client = clientOver(builder, 1);

        assertThatThrownBy(() -> client.forward(OWNER, HttpMethod.POST, PATH, new HttpHeaders(),
                "{}".getBytes()))
                .isInstanceOf(PeerServiceUnavailableException.class);
        server.verify();
    }

    private OwnerProxyClient clientOver(RestClient.Builder builder, Integer readRetries) {
        PerimeterProperties properties = new PerimeterProperties();
        properties.setOwnerUrlTemplate("http://{owner}:8080");
        properties.setReadRetries(readRetries);
        return new OwnerProxyClient(builder, new OwnerAddressResolver(properties), properties);
    }
}
